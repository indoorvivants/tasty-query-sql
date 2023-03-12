import anorm.ParameterValue

enum TupleValue:
  case S(value: String) extends TupleValue
  case B(value: Boolean) extends TupleValue
  case I(value: Int) extends TupleValue

  def asParameterValue: ParameterValue =
    this match
      case S(value) => value
      case B(value) => value
      case I(value) => value

  def asFieldType = this match
    case S(value) => FieldType.S
    case B(value) => FieldType.B
    case I(value) => FieldType.I
end TupleValue

