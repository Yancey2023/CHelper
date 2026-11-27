import { onBeforeUnmount, shallowRef } from 'vue'

export interface SuggestionAudioState {
  index: number
  status: 'loading' | 'playing' | 'paused'
  currentTime: number
  duration: number
}

export function useSuggestionAudio(onError: () => void) {
  const audioPreview = shallowRef<SuggestionAudioState>()
  let player: HTMLAudioElement | undefined
  let playRequest = 0

  function releaseAudio(): void {
    playRequest++
    const previous = player
    player = undefined
    audioPreview.value = undefined
    if (!previous) return
    previous.onloadedmetadata = null
    previous.ondurationchange = null
    previous.ontimeupdate = null
    previous.onpause = null
    previous.onended = null
    previous.onerror = null
    previous.pause()
    previous.removeAttribute('src')
    previous.load()
  }

  function updateProgress(element: HTMLAudioElement): void {
    const state = audioPreview.value
    if (element !== player || !state) return
    const duration = Number.isFinite(element.duration) ? element.duration : 0
    const currentTime = Math.min(element.currentTime, duration)
    if (state.currentTime !== currentTime || state.duration !== duration) {
      audioPreview.value = { ...state, currentTime, duration }
    }
  }

  function setStatus(status: SuggestionAudioState['status']): void {
    const state = audioPreview.value
    if (state && state.status !== status) audioPreview.value = { ...state, status }
  }

  function toggleAudio(index: number, url: string): void {
    if (audioPreview.value?.index !== index || !player) {
      releaseAudio()
      const element = new Audio(url)
      player = element
      audioPreview.value = { index, status: 'paused', currentTime: 0, duration: 0 }
      const update = () => updateProgress(element)
      element.onloadedmetadata = update
      element.ondurationchange = update
      element.ontimeupdate = update
      element.onpause = () => {
        if (player === element && element.paused) setStatus('paused')
      }
      element.onended = () => {
        if (player !== element) return
        update()
        setStatus('paused')
      }
      element.onerror = () => {
        if (player !== element) return
        releaseAudio()
        onError()
      }
      update()
    } else if (audioPreview.value.status !== 'paused') {
      playRequest++
      player.pause()
      updateProgress(player)
      setStatus('paused')
      return
    }

    const element = player
    const request = ++playRequest
    if (element.ended) {
      element.currentTime = 0
      updateProgress(element)
    }
    setStatus('loading')
    void element.play().then(
      () => {
        if (request === playRequest) setStatus('playing')
      },
      () => {
        if (request !== playRequest) return
        releaseAudio()
        onError()
      },
    )
  }

  function seekAudio(index: number, time: number): void {
    const state = audioPreview.value
    if (!player || !state || state.index !== index || !state.duration || !Number.isFinite(time))
      return
    player.currentTime = Math.max(0, Math.min(time, state.duration))
    updateProgress(player)
  }

  onBeforeUnmount(releaseAudio)
  return { audioPreview, toggleAudio, seekAudio, releaseAudio }
}
