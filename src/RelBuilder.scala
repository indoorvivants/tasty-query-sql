class RelBuilder[T](name: String):
  private val b = Vector.newBuilder[BuildAction[T]]
  private val schema = collection.mutable.Set.empty[Field]

  private def getField(name: String, tpe: Extractor[T]) =
    val inst = Field(name, tpe.fieldType)
    if schema(inst) then ???
    else
      schema.add(inst)
      inst

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

  def reference[A: HasId, B: HasId](f: (String, T => A), g: (String, T => B)) =
    storeFK(
      f._1 + "_id",
      Extractor.S(t => HasId[A].identify(f._2(t))),
      ForeignKey(HasId[A].relName, "id")
    )
    storeFK(
      g._1 + "_id",
      Extractor.S(t => HasId[B].identify(g._2(t))),
      ForeignKey(HasId[B].relName, "id")
    )
    this
  end reference

  def build = Indexer(actions = b.result(), tableName = name)
end RelBuilder
