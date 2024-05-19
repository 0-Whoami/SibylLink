package sibyllink.vnc.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import sibyllink.vnc.model.Database
import sibyllink.vnc.model.ServerProfile

class HomeActivityViewModel(app:Application) :BaseViewModel(app) {
    val database=Database(app)

}