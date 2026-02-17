package com.example.apextracker

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_TIME
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromDateString(value: String?): LocalDate? {
        return value?.let {
            LocalDate.parse(it, dateFormatter)
        }
    }

    @TypeConverter
    fun dateToString(date: LocalDate?): String? {
        return date?.format(dateFormatter)
    }

    @TypeConverter
    fun fromTimeString(value: String?): LocalTime? {
        return value?.let {
            LocalTime.parse(it, timeFormatter)
        }
    }

    @TypeConverter
    fun timeToString(time: LocalTime?): String? {
        return time?.format(timeFormatter)
    }

    @TypeConverter
    fun fromDateTimeString(value: String?): LocalDateTime? {
        return value?.let {
            LocalDateTime.parse(it, dateTimeFormatter)
        }
    }

    @TypeConverter
    fun dateTimeToString(dateTime: LocalDateTime?): String? {
        return dateTime?.format(dateTimeFormatter)
    }
}
