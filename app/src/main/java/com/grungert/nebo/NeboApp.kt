package com.grungert.nebo

import android.app.Application
import android.content.Context
import com.grungert.nebo.data.Db
import com.grungert.nebo.data.Settings
import com.grungert.nebo.esm.Esm
import com.grungert.nebo.koan.KoanLibrary
import com.grungert.nebo.koan.Recommender
import com.grungert.nebo.ml.ModelStore
import com.grungert.nebo.ml.Transcriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NeboApp : Application() {
    lateinit var db: Db; private set
    lateinit var settings: Settings; private set
    lateinit var models: ModelStore; private set
    lateinit var transcriber: Transcriber; private set
    lateinit var library: KoanLibrary; private set
    lateinit var recommender: Recommender; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        db = Db(this)
        settings = Settings(this)
        models = ModelStore(this, settings)
        transcriber = Transcriber(db, models, settings)
        library = KoanLibrary(this)
        recommender = Recommender(db, models, library)
        Esm.ensureChannel(this)
        scope.launch {
            Esm.refresh(this@NeboApp)
            com.grungert.nebo.widget.NeboWidget.refresh(this@NeboApp)   // the day of the week koan moves on
            transcriber.drain()
            recommender.warmUp()
            val retag = settings.themesVersion < THEMES_VERSION
            recommender.tagNotes(force = retag)
            if (retag) settings.themesVersion = THEMES_VERSION
            com.grungert.nebo.data.Backup.autoIfDue(this@NeboApp)
        }
        // a note that just became text gets its themes
        scope.launch { transcriber.done.collect { if (it > 0) recommender.tagNotes() } }
    }
}

/** 2: whole-word keywords plus themes by meaning. */
private const val THEMES_VERSION = 2

val Context.app: NeboApp get() = applicationContext as NeboApp
