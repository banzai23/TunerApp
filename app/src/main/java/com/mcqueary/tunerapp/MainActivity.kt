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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mcqueary.tunerapp.databinding.ActivityMainBinding
import java.lang.String.format
import java.util.*

const val SAMPLE_RATE = 44100
const val VOLUME_SENSITIVITY = 2000

private lateinit var viewModel: MainActivity.TunerViewModel

class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding
	var recording = true

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		viewModel = ViewModelProvider(this).get(TunerViewModel::class.java)

		window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN) // hide keyboard

		getMicPermission()
		recordMic()
		println("Passed recordMic!")

		viewModel.frequency.observe(this, { newFreq ->
			binding.tvNote.text = newFreq
		})
	}
	class TunerViewModel: ViewModel() {
		val frequency = MutableLiveData<String>()

		init {
			frequency.value = "0.0"
		}
	}
	class Frequency {
		//	var peak: Short = 0
		//	var valley: Short = 0
		var valleyMark = 0
		var peakMark = 0
		var rising: Boolean = false

		fun frequency(buffSize: Int): Float {
			val multiplyBy: Float = SAMPLE_RATE.toFloat() / buffSize.toFloat()
			return valleyMark.toFloat() * multiplyBy
		}
	}
	private fun recordMic() {
		Thread {
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
			val notes = Notes()
			val minBuffSize: Int = AudioRecord.getMinBufferSize(
				SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT
			)
			val buffSize = 22050 // 22050 is 1 second
			val audioBuffer = ShortArray(buffSize)
			val recorder = AudioRecord(
				MIC,
				SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				buffSize
			)
			recorder.positionNotificationPeriod = buffSize / 2
			recorder.setRecordPositionUpdateListener(object :
				AudioRecord.OnRecordPositionUpdateListener {
				override fun onPeriodicNotification(recorder: AudioRecord) {
					val sampleAmt = recorder.read(audioBuffer, 0, buffSize)
					if (sampleAmt == buffSize) {
						val freq = Frequency()
						freq.rising = audioBuffer[1] > audioBuffer[0]
						for (x in 1 until audioBuffer.size) {
							if (audioBuffer[x] < -VOLUME_SENSITIVITY && !freq.rising) {
								freq.rising = true
								freq.valleyMark++
							}
							if (audioBuffer[x] > VOLUME_SENSITIVITY && freq.rising) {
								freq.rising = false
								freq.peakMark++
							}
						}
						val freqFloat = freq.frequency(buffSize)
						val value = format(Locale.ENGLISH, "%.1f", freqFloat)
						if (freqFloat in 27.5..1567.9) {
							lateinit var noteValue: String

							for (x in notes.freq.indices) {
								if (freqFloat < notes.freq[x]) {
									val freqHit= notes.freq[x] - freqFloat
									val freqBack = freqFloat - notes.freq[x-1]

									if (freqBack < freqHit) {
										noteValue = notes.name[x - 1]
										if (freqBack != 0.0) // don't divide by Zero; higher on bar, hence add
											binding.noteBar.progress = 50 + ((freqBack / freqHit) * 100).toInt()
										else
											binding.noteBar.progress = 50
									} else {
										noteValue = notes.name[x]
										if (freqHit != 0.0) // don't divide by Zero; lower on bar, hence minus
											binding.noteBar.progress = 50 - ((freqHit / freqBack) * 100).toInt()
										else
											binding.noteBar.progress = 50
									}


									break
								}
							}

							viewModel.frequency.value = noteValue
							println("Freq: $value\tNote: $noteValue")
						} else {
							if (viewModel.frequency.value != "0.0") {
								viewModel.frequency.value = "0.0"
								binding.noteBar.progress = 0
							}
						}
					}
				}

				override fun onMarkerReached(recorder: AudioRecord?) {
					// do nothing
				}
			})
			recorder.startRecording()
			while (true) {
				Thread.sleep(30)
				recorder.read(audioBuffer, 0, buffSize)
			}
		}.start()
	}
	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		if (requestCode == 1234) {
			// If request is cancelled, the result arrays are empty.
			if (grantResults.isNotEmpty()
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				//	println("IT WORKED!!! PERMISSION GRANTED!")
			} else {
				finishAffinity()
			}
			return
		} else
			super.onRequestPermissionsResult(requestCode, permissions, grantResults)

	}
	private fun getMicPermission() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
			!= PackageManager.PERMISSION_GRANTED
		) {
			ActivityCompat.requestPermissions(
				this@MainActivity,
				arrayOf(Manifest.permission.RECORD_AUDIO),
				1234
			)
		}
	}
}
class Notes {
	val name = listOf("A0", "Bb0", "B0", "C1", "Db1", "D1", "Eb1", "E1", "F1", "Gb1", "G1", "Ab1", "A1", "Bb1", "B1", "C2", "Db2",
			"D2", "Eb2", "E2", "F2", "Gb2", "G2", "Ab2", "A2", "Bb2", "B2", "C3", "Db3", "D3", "Eb3", "E3", "F3", "Gb3", "G3", "Ab3",
			"A3", "Bb3", "B3", "C4", "Db4", "D4", "Eb4", "E4", "F4", "Gb4", "G4", "Ab4", "A4", "Bb4", "B4", "C5", "Db5", "D5", "Eb5",
			"E5", "F5", "Gb5", "G5", "Ab5", "A5", "Bb5", "B5", "C6", "Db6", "D6", "Eb6", "E6", "F6", "Gb6", "G6")
	val freq = listOf(27.5, 29.1, 30.8, 32.7, 34.6, 36.7, 38.8, 41.2, 43.6, 46.2, 49.0, 51.9, 55.0, 58.2, 61.7, 65.4, 69.3, 73.4,
			77.7, 82.4, 87.3, 92.5, 98.0, 103.8, 110.0, 116.5, 123.4, 130.8, 138.5, 146.8, 155.5, 164.8, 174.6, 185.0, 196.0, 207.6,
			220.0, 233.0, 246.9, 261.6, 277.1, 293.6, 311.1, 329.6, 349.2, 369.9, 392.0, 415.3, 440.0, 466.1, 493.8, 523.2, 554.3, 587.3,
			622.2, 659.2, 698.4, 739.9, 783.9, 830.6, 880.0, 932.3, 987.7, 1046.5, 1108.7, 1174.6, 1244.5, 1318.5, 1396.9, 1479.9, 1567.9)
	/*
	Ab6  	1661.2
	A6	1760.0
	Bb6  	1864.6
	B6	1975.5
	C7	2093.0
	Db7  	2217.4
	D7	2349.3
	Eb7  	2489.0
	E7	2637.0
	F7	2793.8
	Gb7  	2959.9
	G7	3135.9
	Ab7  	3322.4
	A7	3520.0
	Bb7  	3729.3
	B7	3951.0
	C8	4186.0
	Db8  	4434.9
	D8	4698.6
	Eb8  	4978.0
	E8	5274.0
	F8	5587.6
	Gb8  	5919.9
	G8	6271.9
	b8  	6644.8
	A8	7040.0
	Bb8  	7458.6
	B8	7902.1 */
}