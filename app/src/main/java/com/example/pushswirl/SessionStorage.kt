package com.example.pushswirl

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SessionStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("pushswirl_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveSessions(sessions: List<Session>) {
        val json = gson.toJson(sessions)
        prefs.edit().putString("sessions", json).apply()
    }

    fun loadSessions(): List<Session> {
        val json = prefs.getString("sessions", null) ?: return emptyList()
        val type = object : TypeToken<List<Session>>() {}.type
        return gson.fromJson(json, type)
    }

    fun addSession(session: Session) {
        val sessions = loadSessions().toMutableList()
        sessions.add(0, session)
        saveSessions(sessions)
    }

    fun deleteSession(sessionId: Long) {
        val sessions = loadSessions().filter { it.id != sessionId }
        saveSessions(sessions)
    }

    fun deleteAllSessions() {
        saveSessions(emptyList())
    }

    fun getLastSessionConfig(): SessionConfig? {
        return loadSessions().firstOrNull()?.config
    }

    fun calculateStats(): SessionStats {
        val sessions = loadSessions()
        if (sessions.isEmpty()) {
            return SessionStats(0.0, 0.0, 0.0, 0.0, 0.0, 0)
        }

        val smallTTDs = mutableListOf<Double>()
        val mediumTTDs = mutableListOf<Double>()
        val largeTTDs = mutableListOf<Double>()
        val xlTTDs = mutableListOf<Double>()

        sessions.forEach { session ->
            session.phases.forEach { phase ->
                when (phase.size) {
                    PhaseSize.SMALL -> smallTTDs.add(phase.ttdSeconds.toDouble())
                    PhaseSize.MEDIUM -> mediumTTDs.add(phase.ttdSeconds.toDouble())
                    PhaseSize.LARGE -> largeTTDs.add(phase.ttdSeconds.toDouble())
                    PhaseSize.XL -> xlTTDs.add(phase.ttdSeconds.toDouble())
                }
            }
        }

        return SessionStats(
            totalSessions = sessions.size,
            wmaSmallTTD = calculateWMA(smallTTDs),
            wmaMediumTTD = calculateWMA(mediumTTDs),
            wmaLargeTTD = calculateWMA(largeTTDs),
            wmaXlTTD = calculateWMA(xlTTDs),
            wmaSessionLength = calculateWMA(sessions.map { it.totalSeconds.toDouble() }.toList()),
        )
    }
}

fun calculateWMA(values: List<Double>): Double {
    if (values.isEmpty()) {
        return 0.0;
    }

    var weightedSum = 0.0
    var weightTotal = 0.0

    for (i in values.indices) {
        val weight = (i + 1).toDouble() // Weight: 1, 2, 3, ..., n
        weightedSum += values[i] * weight
        weightTotal += weight
    }

    return weightedSum / weightTotal
}