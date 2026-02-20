package com.example.apextracker

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RecurrenceConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromRecurrence(recurrence: Recurrence?): String? {
        return gson.toJson(recurrence)
    }

    @TypeConverter
    fun toRecurrence(recurrenceString: String?): Recurrence? {
        if (recurrenceString == null) {
            return null
        }
        val type = object : TypeToken<Recurrence>() {}.type
        return gson.fromJson(recurrenceString, type)
    }
}
