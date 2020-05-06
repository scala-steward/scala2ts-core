package com.github.scala2ts.model

import scala.collection.immutable.ListSet

object Scala {
  sealed trait TypeDef {
    def name: String
  }

  case class CaseClass(
    name: String,
    fields: ListSet[TypeMember],
    values: ListSet[TypeMember],
    typeArgs: ListSet[String]
  ) extends TypeDef

  case class CaseObject(
    name: String,
    values: ListSet[TypeMember]
  ) extends TypeDef

  case class SealedUnion(
    name: String,
    fields: ListSet[TypeMember],
    possibilities: ListSet[TypeDef]
  ) extends TypeDef

  case class ScalaEnum(
    name: String,
    values: ListSet[String]
  ) extends TypeDef

  case class EnumerationEnum(
    name: String,
    values: ListSet[String]
  ) extends TypeDef

  sealed trait TypeRef

  case class OptionRef(innerType: TypeRef) extends TypeRef

  case class UnionRef(possibilities: ListSet[TypeRef]) extends TypeRef

  case class MapRef(keyType: TypeRef, valueType: TypeRef) extends TypeRef

  case class CaseClassRef(name: String, typeArgs: ListSet[TypeRef]) extends TypeRef

  case class EnumRef(name: String) extends TypeRef

  case class SeqRef(innerType: TypeRef) extends TypeRef

  case class ListSetRef(innerType: TypeRef) extends TypeRef

  case class TypeMember(name: String, typeRef: TypeRef)

  case class UnknownTypeRef(name: String) extends TypeRef

  case class TypeParamRef(name: String) extends TypeRef

  case object IntRef extends TypeRef

  case object LongRef extends TypeRef

  case object DoubleRef extends TypeRef

  case object BooleanRef extends TypeRef

  case object StringRef extends TypeRef

  case object DateRef extends TypeRef

  case object DateTimeRef extends TypeRef
}
