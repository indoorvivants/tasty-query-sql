class Builder[T: HasId]:
  private val b = Vector.newBuilder[BuildAction[T]]
  private val schema = collection.mutable.Set.empty[Field]

  private def getField(name: String, tpe: Extractor[T]) =
    val inst = Field(name, tpe.fieldType)
    if schema(inst) then ???
    else
      schema.add(inst)
      inst

  private val idExtractor = Extractor.S(summon[HasId[T]].identify)

  b += BuildAction.Store(
    getField("id", idExtractor).copy(primaryKey = true),
    idExtractor
  )

  def store(
      n: String,
      f: Extractor[T]
  ) =
    b += BuildAction.Store(getField(n, f), f)
    this
  end store

  def storeFK(
      n: String,
      f: Extractor[T],
      foreignKey: ForeignKey
  ) =
    b += BuildAction.Store(
      getField(n, f).copy(foreignKey = Some(foreignKey)),
      f
    )
    this
  end storeFK

  def storeStr(n: String, f: T => String) =
    store(n, Extractor.S(f))
    this

  def storeInt(n: String, f: T => Int) =
    store(n, Extractor.I(f))
    this

  def storeBool(n: String, f: T => Boolean) =
    store(n, Extractor.B(f))
    this

  def reference[B: HasId](g: T => B | Null) =
    val b = summon[HasId[B]]
    storeFK(
      b.entityName + "_id",
      Extractor.S(t =>
        g(t) match
          case null => null
          case s    => b.identify(s.nn)
      ),
      ForeignKey(b.relName, "id")
    )
    this
  end reference

  def selfReference(tpe: String, g: T => T | Null) =
    val b = summon[HasId[T]]
    storeFK(
      tpe + "_id",
      Extractor.S(t =>
        g(t) match
          case null => null
          case t    => b.identify(t.nn)
      ),
      ForeignKey(b.relName, "id")
    )
    this
  end selfReference

  def build = Indexer(actions = b.result(), tableName = HasId[T].relName)
end Builder
