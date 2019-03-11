package de.gurkenlabs.litiengine.sound;

import java.util.Collection;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public abstract class SoundPlayback implements Runnable {
  protected final SourceDataLine line;
  private FloatControl gainControl;
  private BooleanControl muteControl;

  private Future<?> task = null;

  private final Collection<SoundPlaybackListener> listeners = ConcurrentHashMap.newKeySet();

  private final Collection<VolumeControl> volumeControls = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
  private VolumeControl masterVolume;
  private volatile float miscVolume = 1f;

  /**
   * An object for controlling the volume of an {@code AudioPlayback}. Each distinct instance represents an independent factor contributing to its
   * volume.
   *
   * @see SoundPlayback#createVolumeControl()
   */
  public class VolumeControl {
    private volatile float value = 1f;

    private VolumeControl() {
    }

    public float get() {
      return this.value;
    }

    public void set(float value) {
      if (value < 0f) {
        throw new IllegalArgumentException("negative volume");
      }
      this.value = value;
      SoundPlayback.this.updateVolume();
    }

    @Override
    protected void finalize() {
      // clean up the instance without affecting the volume
      SoundPlayback.this.miscVolume *= this.value;
    }
  }

  SoundPlayback(AudioFormat format) throws LineUnavailableException {
    // acquire resources in the constructor so that they can be used before the task is started
    this.line = AudioSystem.getSourceDataLine(format);
    this.line.open();
    this.line.start();
    this.gainControl = (FloatControl) this.line.getControl(FloatControl.Type.MASTER_GAIN);
    this.muteControl = (BooleanControl) this.line.getControl(BooleanControl.Type.MUTE);
    this.masterVolume = this.createVolumeControl();
  }

  /**
   * Starts playing the audio.
   *
   * @throws IllegalStateException
   *           if the audio has already been started
   */
  public synchronized void start() {
    if (this.task != null) {
      throw new IllegalStateException("already started");
    }
    this.task = SoundEngine.EXECUTOR.submit(this);
  }

  /**
   * Plays a sound to this object's data line.
   *
   * @param sound
   *          The sound to play
   * @return Whether the sound was cancelled while playing
   */
  protected boolean play(Sound sound) {
    byte[] data = sound.getStreamData();
    int len = this.line.getFormat().getFrameSize();
    // math hacks here: we're getting just over half the buffer size, but it needs to be an integral number of sample frames
    len = (this.line.getBufferSize() / len / 2 + 1) * len;
    for (int i = 0; i < data.length; i += this.line.write(data, i, Math.min(len, data.length - i))) {
      if (this.task.isCancelled()) {
        return true;
      }
    }
    return this.task.isCancelled();
  }

  /**
   * Finishes the playback. If this playback was not cancelled in the process, it will notify listeners.
   */
  protected void finish() {
    this.line.drain();
    synchronized (this) {
      if (!this.task.isCancelled()) {
        SoundEvent event = new SoundEvent(this, null);
        for (SoundPlaybackListener listener : this.listeners) {
          listener.finished(event);
        }
        this.line.close();
      }
    }
  }

  /**
   * Adds a <code>SoundPlaybackListener</code> to this instance.
   *
   * @param listener
   *          The <code>SoundPlaybackListener</code> to be added.
   */
  public void addSoundPlaybackListener(SoundPlaybackListener listener) {
    this.listeners.add(listener);
  }

  /**
   * Removes a <code>SoundPlaybackListener</code> from this instance.
   *
   * @param listener
   *          The <code>SoundPlaybackListener</code> to be removed.
   */
  public void removeSoundPlaybackListener(SoundPlaybackListener listener) {
    this.listeners.remove(listener);
  }

  /**
   * Sets the paused state of this playback to the provided value.
   * 
   * @param paused
   *          Whether to pause or resume this playback
   */
  public void setPaused(boolean paused) {
    if (paused) {
      this.pausePlayback();
    } else {
      this.resumePlayback();
    }
  }

  /**
   * Pauses this playback. If this playback is already paused, this call has no effect.
   */
  public void pausePlayback() {
    if (this.line.isOpen()) {
      this.line.stop();
    }
  }

  /**
   * Resumes this playback. If this playback is already playing, this call has no effect.
   */
  public void resumePlayback() {
    if (this.line.isOpen()) {
      this.line.start();
    }
  }

  /**
   * Determines if this playback is paused.
   * 
   * @return Whether this playback is paused
   */
  public boolean isPaused() {
    return !this.line.isActive();
  }

  /**
   * Determines if this playback has sound to play. If it is paused but still in the middle of playback, it will return {@code true}, but it will
   * return {@code false} if it has finished or it has been cancelled.
   * 
   * @return Whether this playback has sound to play
   */
  public boolean isPlaying() {
    return this.line.isOpen();
  }

  /**
   * Attempts to cancel the playback of this audio. If the playback was successfully cancelled, it will notify listeners.
   */
  public void cancel() {
    synchronized (this) {
      if (this.task == null) {
        throw new IllegalStateException("not started");
      }
      if (this.task.cancel(true)) {
        this.line.stop();
        this.line.flush();
        this.line.close();
        SoundEvent event = new SoundEvent(this, null);
        for (SoundPlaybackListener listener : this.listeners) {
          listener.cancelled(event);
        }
      }
    }
  }

  /**
   * Gets the current volume of this playback, considering all {@code VolumeControl} objects created for it.
   * 
   * @return The volume
   */
  public float getMasterVolume() {
    if (this.muteControl.getValue()) {
      return 0f;
    }
    return (float) Math.pow(10.0, this.gainControl.getValue() / 20.0);
  }

  /**
   * Gets the current master volume of this playback. This will be approximately equal to the value set by a previous call to {@code setVolume},
   * though rounding errors may occur.
   * 
   * @return The settable volume
   */
  public float getVolume() {
    return this.masterVolume.get();
  }

  /**
   * Sets the master volume of this playback.
   * 
   * @param volume
   *          The new volume
   */
  public void setVolume(float volume) {
    this.masterVolume.set(volume);
  }

  public VolumeControl getMasterVolumeControl() {
    return this.masterVolume;
  }

  public VolumeControl createVolumeControl() {
    VolumeControl control = new VolumeControl();
    this.volumeControls.add(control);
    return control;
  }

  void updateVolume() {
    synchronized (this.volumeControls) {
      float volume = this.miscVolume;
      for (VolumeControl control : this.volumeControls) {
        volume *= control.get();
      }
      float dbGain = (float) (20.0 * Math.log10(volume));
      if (dbGain < this.gainControl.getMinimum()) {
        this.muteControl.setValue(true);
      } else {
        this.gainControl.setValue(dbGain);
        this.muteControl.setValue(false);
      }
    }
  }

  @Override
  protected void finalize() {
    // resources will not be released if the start method is never called
    if (this.line != null && this.line.isOpen()) {
      this.line.close();
    }
  }
}
