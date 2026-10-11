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
            transcriber.drain()
            recommender.warmUp()
        }
    }
}

val Context.app: NeboApp get() = applicationContext as NeboApp
