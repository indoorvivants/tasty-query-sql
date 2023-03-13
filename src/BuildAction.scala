enum BuildAction[T]:
  case Store(fieldName: Field, extractor: Extractor[T])

  def field = this match
    case Store(fieldName, extractor) => fieldName
