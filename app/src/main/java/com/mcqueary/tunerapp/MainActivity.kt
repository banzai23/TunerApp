package com.mcqueary.tunerapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource.MIC
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mcqueary.tunerapp.databinding.ActivityMainBinding
import java.util.*

const val SAMPLE_RATE = 44100
const val VOLUME_SENSITIVITY = 2000

class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN) // hide keyboard

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
				!= PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), 1234)
		}
		val recordMic = RecordMic()
		recordMic.start()
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		if (requestCode == 1234) {
			// If request is cancelled, the result arrays are empty.
			if (grantResults.isNotEmpty()
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				println("IT WORKED!!! PERMISSION GRANTED!")
			} else {
				finishAffinity()
			}
			return
		} else
			super.onRequestPermissionsResult(requestCode, permissions, grantResults)

	}

	class Frequency {
		//	var peak: Short = 0
		//	var valley: Short = 0
		var valleyMark = 0
		var peakMark = 0
		var rising: Boolean = false

		/*	fun avgPeakOld(): Double {  // suspected not as accurate
				val avgPeak: MutableList<Double> = arrayListOf()
				for (x in 1 until peakMark.size)
					avgPeak.add(peakMark[x].toDouble() - peakMark[x-1].toDouble())
				return avgPeak.average()
			} */
		fun avgPeak(buffSize: Int): Double {
			return (buffSize.toDouble()) / peakMark.toDouble()
		}

		fun actualFreq(buffSize: Int): Double {
			val multiplyBy: Double = SAMPLE_RATE.toDouble() / buffSize.toDouble()
			return valleyMark.toDouble() * multiplyBy
		}
	}

	class RecordMic : Thread() {
		var recording = true
		override fun run() {
			super.run()
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

			val minBuffSize: Int = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
			val buffSize = minBuffSize * 4 // 22050 is 1 second
			val audioBuffer = ShortArray(buffSize)
			val recorder = AudioRecord(MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffSize)
			recorder.positionNotificationPeriod = buffSize / 2
			recorder.setRecordPositionUpdateListener(object : AudioRecord.OnRecordPositionUpdateListener {
				override fun onPeriodicNotification(recorder: AudioRecord) {
					val sampleAmt = recorder.read(audioBuffer, 0, buffSize)
					if (sampleAmt == buffSize) {
						val freq = Frequency()
						freq.rising = audioBuffer[1] > audioBuffer[0]
						for (x in 1 until audioBuffer.size) {
							if (audioBuffer[x] < -VOLUME_SENSITIVITY && !freq.rising) {
								freq.rising = true
								freq.valleyMark++
								println(audioBuffer[x])
							}
							if (audioBuffer[x] > VOLUME_SENSITIVITY && freq.rising) {
								freq.rising = false
								freq.peakMark++
							}
						}
						val actual1: Int = freq.actualFreq(buffSize).toInt()
						println("Actual freq: $actual1")
					}
				}

				override fun onMarkerReached(recorder: AudioRecord?) {
					// do nothing
				}
			})
			recorder.startRecording()
			while (recording) {
				recorder.read(audioBuffer, 0, buffSize)
			}
		}

		fun stopRecording() {
			recording = false
		}
	}
}