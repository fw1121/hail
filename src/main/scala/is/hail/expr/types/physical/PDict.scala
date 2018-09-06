package is.hail.expr.types.physical

import is.hail.annotations.{UnsafeUtils, _}
import is.hail.check.Gen
import is.hail.expr.ir.EmitMethodBuilder
import is.hail.expr.types.TDict
import is.hail.utils._
import org.json4s.jackson.JsonMethods

import scala.reflect.{ClassTag, _}

final case class PDict(keyType: PType, valueType: PType, override val required: Boolean = false) extends PContainer {
  def virtualType: TDict = TDict(keyType.virtualType, valueType.virtualType, required)

  val elementType: PType = +PStruct("key" -> keyType, "value" -> valueType)

  val elementByteSize: Long = UnsafeUtils.arrayElementSize(elementType)

  val contentsAlignment: Long = elementType.alignment.max(4)

  override val fundamentalType: PArray = PArray(elementType.fundamentalType, required)

  override def canCompare(other: PType): Boolean = other match {
    case PDict(okt, ovt, _) => keyType.canCompare(okt) && valueType.canCompare(ovt)
    case _ => false
  }

  override def children = FastSeq(keyType, valueType)

  override def unify(concrete: PType): Boolean = {
    concrete match {
      case PDict(kt, vt, _) => keyType.unify(kt) && valueType.unify(vt)
      case _ => false
    }
  }

  override def subst() = PDict(keyType.subst(), valueType.subst())

  def _toPretty = s"Dict[$keyType, $valueType]"

  override def pyString(sb: StringBuilder): Unit = {
    sb.append("dict<")
    keyType.pyString(sb)
    sb.append(", ")
    valueType.pyString(sb)
    sb.append('>')
  }

  override def _pretty(sb: StringBuilder, indent: Int, compact: Boolean = false) {
    sb.append("Dict[")
    keyType.pretty(sb, indent, compact)
    if (compact)
      sb += ','
    else
      sb.append(", ")
    valueType.pretty(sb, indent, compact)
    sb.append("]")
  }

  def _typeCheck(a: Any): Boolean = a == null || (a.isInstanceOf[Map[_, _]] &&
    a.asInstanceOf[Map[_, _]].forall { case (k, v) => keyType.typeCheck(k) && valueType.typeCheck(v) })

  override def genNonmissingValue: Gen[Annotation] =
    Gen.buildableOf2[Map](Gen.zip(keyType.genValue, valueType.genValue))

  override def valuesSimilar(a1: Annotation, a2: Annotation, tolerance: Double, absolute: Boolean): Boolean =
    a1 == a2 || (a1 != null && a2 != null &&
      a1.asInstanceOf[Map[Any, _]].outerJoin(a2.asInstanceOf[Map[Any, _]])
        .forall { case (_, (o1, o2)) =>
          o1.liftedZip(o2).exists { case (v1, v2) => valueType.valuesSimilar(v1, v2, tolerance, absolute) }
        })

  override def scalaClassTag: ClassTag[Map[_, _]] = classTag[Map[_, _]]

  val ordering: ExtendedOrdering =
    ExtendedOrdering.mapOrdering(elementType.ordering)

  def codeOrdering(mb: EmitMethodBuilder, other: PType): CodeOrdering = {
    assert(other isOfType this)
    CodeOrdering.mapOrdering(this, other.asInstanceOf[PDict], mb)
  }
}