import { create } from "zustand";

interface VoiceState {
  /** 是否正在录音 */
  isRecording: boolean;
  /** 是否正在播放 */
  isPlaying: boolean;
  /** 当前播放的音频URL */
  currentAudioUrl: string | null;
  /** 录音时长(ms) */
  recordingDuration: number;

  /** 设置录音状态 */
  setRecording: (recording: boolean) => void;
  /** 设置播放状态 */
  setPlaying: (playing: boolean) => void;
  /** 设置当前音频 */
  setCurrentAudio: (url: string | null) => void;
  /** 设置录音时长 */
  setRecordingDuration: (duration: number) => void;
}

export const useVoiceStore = create<VoiceState>((set) => ({
  isRecording: false,
  isPlaying: false,
  currentAudioUrl: null,
  recordingDuration: 0,

  setRecording: (recording) => set({ isRecording: recording }),
  setPlaying: (playing) => set({ isPlaying: playing }),
  setCurrentAudio: (url) => set({ currentAudioUrl: url }),
  setRecordingDuration: (duration) => set({ recordingDuration: duration }),
}));
