package com.sliit.android.thecure.DB;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;


@Dao
public interface DysgraphiaDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertDysgraphiaData(Dysgraphia... dysgraphias);

    @Query("SELECT * FROM Dysgraphia ORDER BY datetime DESC")
    Dysgraphia[] loadAllDysgraphiaData();

    @Query("SELECT * FROM Dysgraphia WHERE id LIKE :id")
    Dysgraphia findById(String id);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updateDysgraphiaData(Dysgraphia dysgraphia);

    @Query("DELETE FROM Dysgraphia WHERE id LIKE :id")
    void deleteNote(String id);

    @Query("DELETE FROM Dysgraphia")
    void deleteAllNotes();

    @Query("UPDATE Dysgraphia SET percentage1 = :percentage WHERE id LIKE :id")
    void updatePercentage1(String id, String percentage);

    @Query("UPDATE Dysgraphia SET percentage2 = :percentage WHERE id LIKE :id")
    void updatePercentage2(String id, String percentage);

    @Query("UPDATE Dysgraphia SET percentage3 = :percentage WHERE id LIKE :id")
    void updatePercentage3(String id, String percentage);

    @Query("SELECT id FROM Dysgraphia WHERE datetime LIKE :datetime")
    String findByDateTime(Long datetime);

}