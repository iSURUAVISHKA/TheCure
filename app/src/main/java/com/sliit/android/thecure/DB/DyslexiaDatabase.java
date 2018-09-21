package com.sliit.android.thecure.DB;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

/**
 * Dyslexia Database
 * Get instance of the database
 */
@Database(entities = {Dyslexia.class,Dysgraphia.class}, version = 1, exportSchema = false)
public abstract class DyslexiaDatabase extends RoomDatabase {

    private static DyslexiaDatabase INSTANCE;

    public abstract DyslexiaDao dyslexiaDao();
    public abstract DysgraphiaDao dysgraphiaDao();

    public static DyslexiaDatabase getDyslexiaDatabase(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(context.getApplicationContext(), DyslexiaDatabase.class, "AppDatabase")
                            .allowMainThreadQueries()
                            .build();
        }
        return INSTANCE;
    }

    public static void destroyInstance() {
        INSTANCE = null;
    }
}
