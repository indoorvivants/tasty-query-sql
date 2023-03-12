enum Extractor[T](val fieldType: FieldType):
  case S[T](f: T => String | Null) extends Extractor[T](FieldType.S)
  case B[T](f: T => Boolean) extends Extractor[T](FieldType.B)
  case I[T](f: T => Int) extends Extractor[T](FieldType.I)

  def extract(v: T) = this match
    case S(f) =>
      Option(f(v)).map(TupleValue.S.apply)
    case B(f) =>
      Option(f(v)).map(TupleValue.B.apply)
    case I(f) =>
      Option(f(v)).map(TupleValue.I.apply)
end Extractor
