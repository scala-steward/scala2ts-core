package com.github.scala2ts.core

import scala.collection.immutable.ListSet
import scala.reflect.api.Universe

final class ScalaParser[U <: Universe](
  universe: U
) {
  import com.github.scala2ts.model.Scala.{TypeRef => ScalaTypeRef, _}
  import universe.{ClassSymbol, MethodSymbol, ModuleSymbol, NoSymbol, NullaryMethodType, SingleTypeApi, Symbol, Type, TypeRef, typeOf}

  def parseTypes(types: List[Type]): ListSet[TypeDef] =
    parse(types, ListSet.empty[Type], ListSet.empty[TypeDef])

  private def parseType(tpe: Type): Option[TypeDef] = tpe match {
    case _: SingleTypeApi =>
      parseObject(tpe)

    case _ if (tpe.getClass.getName contains "ModuleType" /*Workaround*/ ) =>
      parseObject(tpe)

    case _ if tpe.typeSymbol.isClass =>
      val classSym = tpe.typeSymbol.asClass

      if (classSym.isTrait && classSym.isSealed && tpe.typeParams.isEmpty) {
        parseSealedUnion(tpe)
      } else if (isCaseClass(tpe) && !isAnyValChild(tpe)) {
        parseCaseClass(tpe) // TODO: Special case for ValueClass
      } else {
        Option.empty[TypeDef]
      }

    case _ =>
      // logger.warning(s"Unsupported Scala type: $tpe")
      Option.empty[TypeDef]
  }

  private object Field {
    def unapply(m: MethodSymbol): Option[MethodSymbol] = m match {
      case m: MethodSymbol if (!m.isAbstract && m.isPublic && !m.isImplicit &&
        m.paramLists.forall(_.isEmpty) &&
        {
          val n = m.name.toString
          !(n.contains("$") || n.startsWith("<"))
        } &&
        m.overrides.forall { o =>
          val declaring = o.owner.fullName

          !declaring.startsWith("java.") &&
            !declaring.startsWith("scala.")
        }) => Some(m)

      case _ => None
    }
  }

  private def parseObject(tpe: Type): Option[CaseObject] = {
    val members = tpe.decls.collect {
      case Field(m) => member(m, List.empty)
    }

    Some(CaseObject(
      tpe.typeSymbol.name.toString stripSuffix ".type",
      ListSet.empty ++ members
    ))
  }

  private def parseSealedUnion(tpe: Type): Option[SealedUnion] = {
    // TODO: Check & warn there is no type parameters for a union type

    // Members
    val members = tpe.decls.collect {
      case m: MethodSymbol if (m.isAbstract && m.isPublic && !m.isImplicit &&
        !m.name.toString.endsWith("$")) => member(m, List.empty)
    }

    directKnownSubclasses(tpe) match {
      case possibilities @ (_ :: _) =>
        Some(SealedUnion(
          tpe.typeSymbol.name.toString,
          ListSet.empty ++ members,
          parseTypes(possibilities)
        ))

      case _ => Option.empty[SealedUnion]
    }
  }

  private def parseCaseClass(caseClassType: Type): Option[CaseClass] = {
    val typeParams = caseClassType
      .typeConstructor
      .dealias
      .typeParams
      .map(_.name.decodedName.toString)

    // Members
    val members = caseClassType.members.collect {
      case Field(m) if m.isCaseAccessor =>
        member(m, typeParams)
    }.toList

    val values = caseClassType.decls.collect {
      case Field(m) =>
        member(m, typeParams)
    }.filterNot(members.contains)

    Some(CaseClass(
      caseClassType.typeSymbol.name.toString,
      ListSet.empty ++ members,
      ListSet.empty ++ values,
      ListSet.empty ++ typeParams
    ))
  }

  @inline private def member(
    sym: MethodSymbol,
    typeParams: List[String]
  ): TypeMember = TypeMember(
    sym.name.toString,
    scalaTypeRef(
      sym.returnType.map(_.dealias),
      typeParams.toSet
    ))

  @annotation.tailrec
  private def parse(
    types: List[Type],
    examined: ListSet[Type],
    parsed: ListSet[TypeDef]
  ): ListSet[TypeDef] = types match {
    case scalaType :: tail =>
      if (
        !examined.contains(scalaType) &&
        !scalaType.typeSymbol.isParameter
      ) {

        val relevantMemberSymbols = scalaType.members.collect {
          case m: MethodSymbol if m.isCaseAccessor => m
        }

        val memberTypes = relevantMemberSymbols.map(
          _.typeSignature.map(_.dealias) match {
            case NullaryMethodType(resultType) => resultType
            case t => t.map(_.dealias)
          })

        val typeArgs = scalaType match {
          case t: TypeRef =>
            t.args

          case _ => ListSet.empty[Type]
        }

        parse(
          memberTypes ++: typeArgs ++: tail,
          examined + scalaType,
          parsed ++ parseType(scalaType)
        )

      } else {
        parse(
          tail,
          examined + scalaType,
          parsed ++ parseType(scalaType)
        )
      }

    case _ => parsed
  }

  // TODO: resolve from implicit (typeclass)
  private def scalaTypeRef(scalaType: Type, typeParams: Set[String]): ScalaTypeRef = {
    scalaType.typeSymbol.name.toString match {
      case "Int" | "Byte" | "Short" =>
        IntRef
      case "Long" =>
        LongRef
      case "Double" =>
        DoubleRef
      case "Boolean" =>
        BooleanRef
      case "String" =>
        StringRef
      case "List" | "ListSet" | "Set" | "Seq" =>
        val innerType = scalaType.asInstanceOf[TypeRef].args.head
        SeqRef(scalaTypeRef(innerType, typeParams))
      case "Option" =>
        val innerType = scalaType.asInstanceOf[TypeRef].args.head
        OptionRef(scalaTypeRef(innerType, typeParams))
      case "LocalDate" =>
        DateRef
      case "Instant" | "Timestamp" | "LocalDateTime" | "ZonedDateTime" =>
        DateTimeRef
      case typeParam if typeParams.contains(typeParam) =>
        TypeParamRef(typeParam)
      case _ if isAnyValChild(scalaType) =>
        scalaTypeRef(scalaType.members.filter(!_.isMethod).map(_.typeSignature).head, Set())
      case _ if isCaseClass(scalaType) =>
        val caseClassName = scalaType.typeSymbol.name.toString
        val typeArgs = scalaType.asInstanceOf[TypeRef].args
        val typeArgRefs = typeArgs.map(scalaTypeRef(_, typeParams))

        CaseClassRef(caseClassName, ListSet.empty ++ typeArgRefs)

      case "Either" => {
        val innerTypeL = scalaType.asInstanceOf[TypeRef].args.head
        val innerTypeR = scalaType.asInstanceOf[TypeRef].args.last

        UnionRef(ListSet(
          scalaTypeRef(innerTypeL, typeParams),
          scalaTypeRef(innerTypeR, typeParams)))
      }

      case "Map" =>
        val keyType = scalaType.asInstanceOf[TypeRef].args.head
        val valueType = scalaType.asInstanceOf[TypeRef].args.last
        MapRef(scalaTypeRef(keyType, typeParams), scalaTypeRef(valueType, typeParams))
      case unknown =>
        //println(s"type ref $typeName umkown")
        UnknownTypeRef(unknown)
    }
  }

  @inline private def isCaseClass(scalaType: Type): Boolean =
    scalaType.typeSymbol.isClass && scalaType.typeSymbol.asClass.isCaseClass

  @inline private def isAnyValChild(scalaType: Type): Boolean =
    scalaType <:< typeOf[AnyVal]

  private def directKnownSubclasses(tpe: Type): List[Type] = {
    // Workaround for SI-7046: https://issues.scala-lang.org/browse/SI-7046
    val tpeSym = tpe.typeSymbol.asClass

    @annotation.tailrec
    def allSubclasses(path: Iterable[Symbol], subclasses: Set[Type]): Set[Type] = path.headOption match {
      case Some(cls: ClassSymbol) if (
        tpeSym != cls && cls.selfType.baseClasses.contains(tpeSym)) => {
        val newSub: Set[Type] = if (!cls.isCaseClass) {
          // logger.warning(s"cannot handle class ${cls.fullName}: no case accessor")
          Set.empty
        } else if (cls.typeParams.nonEmpty) {
          // logger.warning(s"cannot handle class ${cls.fullName}: type parameter not supported")
          Set.empty
        } else Set(cls.selfType)

        allSubclasses(path.tail, subclasses ++ newSub)
      }

      case Some(o: ModuleSymbol) if (
        o.companion == NoSymbol && // not a companion object
          o.typeSignature.baseClasses.contains(tpeSym)) =>
        allSubclasses(path.tail, subclasses + o.typeSignature)

      case Some(o: ModuleSymbol) if (
        o.companion == NoSymbol // not a companion object
        ) => allSubclasses(path.tail, subclasses)

      case Some(_) => allSubclasses(path.tail, subclasses)

      case _ => subclasses
    }

    if (tpeSym.isSealed && tpeSym.isAbstract) {
      allSubclasses(tpeSym.owner.typeSignature.decls, Set.empty).toList
    } else List.empty
  }

}
