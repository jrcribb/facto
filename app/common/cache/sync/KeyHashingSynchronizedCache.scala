package common.cache.sync

import com.google.common.hash.HashCode
import common.cache.UniquelyHashable

private[sync] final class KeyHashingSynchronizedCache[K <: UniquelyHashable, V <: Object](delegate: SynchronizedCache[HashCode, V])
  extends SynchronizedCache[K, V] {

  override def getOrCalculate(key: K, calculateValueFunc: () => V): V =
    delegate.getOrCalculate(key.uniqueHash, calculateValueFunc)

  override def invalidate(key: K): Unit =
    delegate.invalidate(key.uniqueHash)

  override def foreachWithLock(f: (V) => Unit): Unit =
    delegate.foreachWithLock(f)
}
