case class Indexer[T](
    actions: Vector[BuildAction[T]]
):
  def go(s: T) =
    val extracted = actions.map {
      case BuildAction.Store(fieldName, extractor) =>
        fieldName -> extractor.extract(s)
    }.toMap

    extracted

  def fields = actions.map(_.field)
end Indexer
