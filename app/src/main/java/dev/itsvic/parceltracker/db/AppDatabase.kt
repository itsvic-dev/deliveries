// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    version = 8,
    entities =
        [
          Parcel::class,
          ParcelStatus::class,
          ParcelHistoryItem::class,
          AllegroPackageLink::class,
          OlxPackageLink::class,
        ],
    autoMigrations =
        [
            AutoMigration(from = 1, to = 2),
            AutoMigration(from = 2, to = 3),
            AutoMigration(from = 2, to = 4),
            AutoMigration(from = 3, to = 4),
            AutoMigration(from = 4, to = 5),
            AutoMigration(from = 5, to = 6),
            AutoMigration(from = 6, to = 7),
            AutoMigration(from = 7, to = 8),
        ])
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun parcelDao(): ParcelDao

  abstract fun parcelStatusDao(): ParcelStatusDao

  abstract fun parcelHistoryDao(): ParcelHistoryDao

  abstract fun allegroPackageLinkDao(): AllegroPackageLinkDao

  abstract fun olxPackageLinkDao(): OlxPackageLinkDao
}
