package fr.curlyspiker.jpics

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tag")
data class PicTag(
    @PrimaryKey var tagId : Int,
    var name : String)