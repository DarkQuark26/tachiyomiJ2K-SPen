/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package tachiyomi.domain.catalog.interactor

import io.reactivex.Flowable
import io.reactivex.rxkotlin.Flowables
import tachiyomi.domain.catalog.model.Catalog
import tachiyomi.domain.catalog.model.CatalogLocal
import tachiyomi.domain.catalog.model.CatalogSort
import tachiyomi.domain.catalog.repository.CatalogRepository
import tachiyomi.domain.library.repository.LibraryRepository
import javax.inject.Inject

class GetLocalCatalogs @Inject constructor(
  private val catalogRepository: CatalogRepository,
  private val libraryRepository: LibraryRepository
) {

  // TODO should we defer this call?
  fun interact(sort: CatalogSort = CatalogSort.Name): Flowable<List<Catalog>> {
    val catalogsFlow = Flowables.combineLatest(
      catalogRepository.getInternalCatalogsFlowable(),
      catalogRepository.getInstalledCatalogsFlowable()
    ) { internal, installed ->
      internal + installed
    }

    return when (sort) {
      CatalogSort.Name -> catalogsFlow.map { catalogs -> catalogs.sortedBy { it.name } }
      CatalogSort.Favorites -> {
        val favoriteIdsFlow = libraryRepository.getFavoriteSourceIds()
          .map { favoriteIds ->
            var position = 0
            favoriteIds.associateWith { position++ }
          }
          .toFlowable()

        Flowables.combineLatest(
          catalogsFlow,
          favoriteIdsFlow
        ) { catalogs, favoriteIds ->
          val favoritesComparator = FavoritesComparator(favoriteIds)
          catalogs.sortedWith(favoritesComparator.thenBy { it.name })
        }
      }
    }
  }

  private class FavoritesComparator(
    private val favoriteIds: Map<Long, Int>
  ) : Comparator<CatalogLocal> {

    override fun compare(c1: CatalogLocal, c2: CatalogLocal): Int {
      val pos1 = favoriteIds.getOrElse(c1.source.id) { Int.MAX_VALUE }
      val pos2 = favoriteIds.getOrElse(c2.source.id) { Int.MAX_VALUE }
      return pos1.compareTo(pos2)
    }
  }

}