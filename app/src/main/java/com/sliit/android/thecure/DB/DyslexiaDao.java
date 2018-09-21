package com.sliit.android.thecure.DB;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;


@Dao
public interface DyslexiaDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertDyslexiaData(Dyslexia... dyslexias);

    @Query("SELECT * FROM Dyslexia ORDER BY datetime DESC")
    Dyslexia[] loadAllDyslexiaData();

    @Query("SELECT * FROM Dyslexia WHERE id LIKE :id")
    Dyslexia findById(String id);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updateDyslexiaData(Dyslexia dyslexia);

    @Query("DELETE FROM Dyslexia WHERE id LIKE :id")
    void deleteNote(String id);

    @Query("DELETE FROM Dyslexia")
    void deleteAllNotes();

    @Query("UPDATE Dyslexia SET percentage1 = :percentage WHERE id LIKE :id")
    void updatePercentage1(String id, String percentage);

    @Query("UPDATE Dyslexia SET percentage2 = :percentage WHERE id LIKE :id")
    void updatePercentage2(String id, String percentage);

    @Query("UPDATE Dyslexia SET percentage3 = :percentage WHERE id LIKE :id")
    void updatePercentage3(String id, String percentage);

    @Query("SELECT id FROM Dyslexia WHERE datetime LIKE :datetime")
    String findByDateTime(Long datetime);

}