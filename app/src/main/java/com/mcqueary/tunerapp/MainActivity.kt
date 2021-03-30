package com.mcqueary.tunerapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource.MIC
import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.mcqueary.tunerapp.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.lang.String.format
import java.util.*

const val SAMPLE_RATE = 44100
const val ONE_SECOND_IN_SAMPLES = SAMPLE_RATE / 2 // currently not in use
const val VOLUME_SENSITIVITY = 2000.toShort()

class MainActivity : AppCompatActivity() {
	private lateinit var viewModel: TunerViewModel
	private lateinit var binding: ActivityMainBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		// bind the layout, then bind the viewModel
		viewModel = ViewModelProvider(this).get(TunerViewModel::class.java)


		// TextView Note and Frequency, and ProgressBar NoteBar binding to liveData in viewModel
		// in the following code
		viewModel.note.observe( this, {newNote ->
			binding.tvNote.text = newNote
		})
		viewModel.frequency.observe(this, { newFreq ->
			binding.tvFreq.text = newFreq
		})
		viewModel.noteBar.observe(this, {newProgress ->
			binding.noteBar.progress = newProgress
		})
		viewModel.noteBarColor.observe(this, {newColor ->
			binding.noteBar.setBackgroundColor(newColor)
		})
		getMicPermission() // program exits if user doesn't allow Mic Permission
		// if permission has already been previously granted, nothing shows up

		GlobalScope.launch(Dispatchers.Default) {
			recordMic(viewModel)   // launch audio recording of Mic on separate thread, not Main Thread
		}
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
	private suspend fun recordMic(viewModel: TunerViewModel) = withContext(Dispatchers.Default) {
		Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
		val buffSize: Int = AudioRecord.getMinBufferSize(
				SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT
		) * 4
		val readSamples = ONE_SECOND_IN_SAMPLES / 2 //
		val abSize = buffSize * 8   // 8 buffers worth of total stored in audioBuffer
		val audioBuffer = ShortArray(abSize){0}

		val recorder = AudioRecord(
				MIC,
				SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				buffSize
		)
		recorder.startRecording()

		val notes = Notes()

		while (true) {
			val sampleAmt = recorder.read(audioBuffer, 0, buffSize)
			if (sampleAmt == buffSize) { // recorder.read returns the number of samples read, this checks
				val freq = Frequency()  // and continues if the buffSize is full
				freq.rising = audioBuffer[1] > audioBuffer[0]
				for (x in 1 until readSamples) {
					if (audioBuffer[x-1] == 0.toShort() &&
							audioBuffer[x] == 0.toShort() && // if 3 samples in a row are zeros
							audioBuffer[x+1] == 0.toShort()) {
						continue
					}
					if (audioBuffer[x] < -VOLUME_SENSITIVITY && !freq.rising) {
						freq.rising = true
					}

					if (audioBuffer[x] > VOLUME_SENSITIVITY && freq.rising) {
						freq.rising = false
						freq.peakMark++

						if (!freq.sampleStart) {
							freq.sampleStart = true
							freq.sampleCount = 0    // reset sampleCount to start here
						}
					}

					if (audioBuffer[x] > freq.peak)
						freq.peak = audioBuffer[x]

					freq.sampleCount++
				}
				// at the end of this calculation, rewind the frequency until we match where
				// we started on the frequency, which is around VOLUME_SENSITIVITY
				val lastWave = audioBuffer[readSamples - 1]
				if (freq.rising || (!freq.rising && lastWave <= VOLUME_SENSITIVITY)) {
					for (x in 1 until readSamples) {
						if (audioBuffer[readSamples - x] > VOLUME_SENSITIVITY) {
							freq.sampleCount -= x
							break
						}
					}
				} else if (!freq.rising && lastWave > VOLUME_SENSITIVITY) {
					for (x in 1 until readSamples) {
						if (audioBuffer[readSamples - x] < VOLUME_SENSITIVITY) {
							freq.sampleCount -= x
							break
						}
					}
				}

				val freqFloat = freq.frequency()

				val value = format(Locale.ENGLISH, "%.1f", freqFloat)
				if (freqFloat in 27.5..1567.9) {
					lateinit var noteValue: String
					var noteBarColorValue = R.color.orange
					var noteBarValue = 0
					for (x in notes.freq.indices) {
						if (freqFloat < notes.freq[x]) {
							val freqHit = notes.freq[x] - freqFloat
							val freqBack = freqFloat - notes.freq[x - 1]

							if (freqBack < freqHit) {
								noteValue = notes.name[x - 1]
								if (freqHit != 0.0) // don't divide by Zero; higher on bar, hence add
									noteBarValue =
											50 + ((freqBack / freqHit) * 100.toDouble()).toInt()
								else
									noteBarValue = 50
							} else {
								noteValue = notes.name[x]
								if (freqBack != 0.0) // don't divide by Zero; lower on bar, hence minus
									noteBarValue =
											50 - ((freqHit / freqBack) * 100.toDouble()).toInt()
								else
									noteBarValue = 50

								if (noteBarValue > 45 || noteBarValue < 55)
									noteBarColorValue = R.color.green
							}
							break
						}
					}
					launch(Dispatchers.Main) {
						viewModel.frequency.value = value
						viewModel.note.value = noteValue
						viewModel.noteBar.value = noteBarValue
						viewModel.noteBarColor.value = noteBarColorValue
						println("Freq: $value\tNote: $noteValue")
					}
				} else {
					if (viewModel.frequency.value != "0.0") {
						launch(Dispatchers.Main) {
							viewModel.frequency.value = "0.0"
							viewModel.note.value = "☺"
							viewModel.noteBar.value = 0
						}
					}
				}
			}

			for (x in buffSize until audioBuffer.size)
				audioBuffer[x] = audioBuffer[x-buffSize]
		}
	}
}
// TODO: Improve accuracy of frequency calculation