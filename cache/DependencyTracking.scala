package xsbt

private object DependencyTracking
{
	import scala.collection.mutable.{Set, HashMap, Map, MultiMap}
	type DependencyMap[T] = HashMap[T, Set[T]] with MultiMap[T, T]
	def newMap[T]: DependencyMap[T] = new HashMap[T, Set[T]] with MultiMap[T, T]
	type TagMap[T] = Map[T, Array[Byte]]
	def newTagMap[T] = new HashMap[T, Array[Byte]]
}

trait UpdateTracking[T] extends NotNull
{
	def dependency(source: T, dependsOn: T): Unit
	def use(source: T, uses: T): Unit
	def product(source: T, output: T): Unit
	def tag(source: T, t: Array[Byte]): Unit
	def read: ReadTracking[T]
}
import scala.collection.Set
trait ReadTracking[T] extends NotNull
{
	def dependsOn(file: T): Set[T]
	def products(file: T): Set[T]
	def sources(file: T): Set[T]
	def usedBy(file: T): Set[T]
	def allProducts: Set[T]
	def allSources: Set[T]
	def allUsed: Set[T]
	def allTags: Seq[(T,Array[Byte])]
}
import DependencyTracking.{DependencyMap => DMap, newMap, TagMap}
private final class DefaultTracking[T](translateProducts: Boolean)
	(val reverseDependencies: DMap[T], val reverseUses: DMap[T], val sourceMap: DMap[T], val tagMap: TagMap[T])
	extends DependencyTracking[T](translateProducts)
{
	val productMap: DMap[T] = forward(sourceMap) // map from a source to its products.  Keep in sync with sourceMap
}
// if translateProducts is true, dependencies on a product are translated to dependencies on a source
private abstract class DependencyTracking[T](translateProducts: Boolean) extends ReadTracking[T] with UpdateTracking[T]
{
	val reverseDependencies: DMap[T] // map from a file to the files that depend on it
	val reverseUses: DMap[T] // map from a file to the files that use it
	val sourceMap: DMap[T] // map from a product to its sources.  Keep in sync with productMap
	val productMap: DMap[T] // map from a source to its products.  Keep in sync with sourceMap
	val tagMap: TagMap[T]

	def read = this

	final def dependsOn(file: T): Set[T] = get(reverseDependencies, file)
	final def products(file: T): Set[T] = get(productMap, file)
	final def sources(file: T): Set[T] = get(sourceMap, file)
	final def usedBy(file: T): Set[T] = get(reverseUses, file)
	final def tag(file: T): Array[Byte] = tagMap.getOrElse(file, new Array[Byte](0))

	final def allProducts = Set() ++ sourceMap.keys
	final def allSources = Set() ++ productMap.keys
	final def allUsed = Set() ++ reverseUses.keys
	final def allTags = tagMap.toSeq

	private def get(map: DMap[T], value: T): Set[T] = map.getOrElse(value, Set.empty[T])

	final def dependency(sourceFile: T, dependsOn: T)
	{
		val actualDependencies =
			if(!translateProducts)
				Seq(dependsOn)
			else
				sourceMap.getOrElse(dependsOn, Seq(dependsOn))
		actualDependencies.foreach { actualDependency => reverseDependencies.add(actualDependency, sourceFile) }
	}
	final def product(sourceFile: T, product: T)
	{
		productMap.add(sourceFile, product)
		sourceMap.add(product, sourceFile)
	}
	final def use(sourceFile: T, usesFile: T) { reverseUses.add(usesFile, sourceFile) }
	final def tag(sourceFile: T, t: Array[Byte]) { tagMap(sourceFile) = t }

	final def removeAll(files: Iterable[T])
	{
		def remove(a: DMap[T], b: DMap[T], file: T): Unit =
			for(x <- a.removeKey(file)) b --= x
		def removeAll(a: DMap[T], b: DMap[T]): Unit =
			files.foreach { file => remove(a, b, file); remove(b, a, file) }

		removeAll(forward(reverseDependencies), reverseDependencies)
		removeAll(productMap, sourceMap)
		removeAll(forward(reverseUses), reverseUses)
		tagMap --= files
	}
	protected final def forward(map: DMap[T]): DMap[T] =
	{
		val f = newMap[T]
		for( (key, values) <- map; value <- values) f.add(value, key)
		f
	}
}