package fr.curlyspiker.jpics

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class User(@PrimaryKey val userId: Int, val username: String) {
    var email: String = ""
    var status = ""
}