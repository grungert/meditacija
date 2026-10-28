package com.grungert.nebo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
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
    /** True while a session is open: nothing from Nebo rings into a sit. */
    @Volatile var sitting = false
    lateinit var settings: Settings; private set
    lateinit var models: ModelStore; private set
    lateinit var transcriber: Transcriber; private set
    lateinit var library: KoanLibrary; private set
    lateinit var recommender: Recommender; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        com.grungert.nebo.data.Backup.applyPending(this)   // a restored backup takes its place before anything opens
        db = Db(this)
        settings = Settings(this)
        // the person's own labels for the points, read from the words like the named ones
        com.grungert.nebo.koan.FourPointsReader.custom = listOf(com.grungert.nebo.koan.FourPointsReader.EMOTIONS, com.grungert.nebo.koan.FourPointsReader.BODY,
            com.grungert.nebo.koan.FourPointsReader.URGES).associateWith { settings.customLabels(it) }
        com.grungert.nebo.sky.Storm.thunder = settings.thunder
        models = ModelStore(this, settings)
        transcriber = Transcriber(db, models, settings)
        library = KoanLibrary(this)
        recommender = Recommender(db, models, library)
        Esm.ensureChannel(this)
        scope.launch {
            Esm.refresh(this@NeboApp)
            // once: transcripts a model wrote in Cyrillic become Latin, and their themes are found again
            if (!settings.transcriptsLatin) {
                for (e in db.entries()) if (e.transcript?.let { com.grungert.nebo.ml.Latin.hasCyrillic(it) } == true)
                    db.updateEntry(e.copy(transcript = com.grungert.nebo.ml.Latin.of(e.transcript), themes = emptyList()),
                        com.grungert.nebo.data.Col.TRANSCRIPT, com.grungert.nebo.data.Col.THEMES)
                settings.transcriptsLatin = true
            }
            transcriber.drain()   // nothing to do unless a recording waits for its text, and then it is worth doing
        }
        // a note that just became text gets its themes
        scope.launch { transcriber.done.collect { if (it > 0) { recommender.tagNotes(); recommender.warmSearch() } } }
        // The meaning model, the widget's sky and the weekly copy wait until the person opens the app. Most starts of
        // this process are an alarm, a widget redraw or a reply from a notification, gone again in seconds; doing all
        // of it there cost about five seconds of processor and 400 MB each time.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var warmed = false
            override fun onActivityStarted(activity: Activity) {
                if (warmed) return
                warmed = true
                scope.launch { warm() }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private suspend fun warm() {
        com.grungert.nebo.widget.NeboWidget.refresh(this)   // the day of the week koan moves on
        recommender.warmUp()
        // again for every note when the way of finding themes changed, or when the meaning model has arrived since
        val meaning = recommender.usesMeaning
        val retag = settings.themesVersion < THEMES_VERSION || (meaning && !settings.themesByMeaning)
        recommender.tagNotes(force = retag)
        if (retag) { settings.themesVersion = THEMES_VERSION; settings.themesByMeaning = meaning }
        recommender.warmSearch()
        com.grungert.nebo.data.Backup.autoIfDue(this)
    }
}

/** 2: whole-word keywords plus themes by meaning. 3: tighter words, negation, themes from the words only. */
private const val THEMES_VERSION = 3

val Context.app: NeboApp get() = applicationContext as NeboApp
