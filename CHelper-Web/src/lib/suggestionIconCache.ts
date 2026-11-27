export const suggestionIconPixels = 56

const memoryLimit = 256
const diskLimit = 512
const maxDiskIconBytes = 128 * 1024
const maxDiskAge = 7 * 24 * 60 * 60 * 1000
const parallelLoads = 6
const cacheName = 'chelper-suggestion-icons-v1'
const cachedAtHeader = 'x-chelper-cached-at'

type PaintIcon = (icon: HTMLCanvasElement) => void

const thumbnails = new Map<string, HTMLCanvasElement>()
const requests = new Map<string, { started: boolean; listeners: Set<PaintIcon> }>()
let activeLoads = 0
let diskCache: Promise<Cache | undefined> | undefined

function openDiskCache(): Promise<Cache | undefined> {
  // Storage can be unavailable on insecure origins or blocked by the browser.
  return (diskCache ??= Promise.resolve()
    .then(() => globalThis.caches?.open(cacheName))
    .catch(() => undefined))
}

async function storeIcon(cache: Cache, url: string, blob: Blob): Promise<void> {
  if (blob.size > maxDiskIconBytes) return
  await cache.put(
    url,
    new Response(blob, {
      headers: { 'content-type': blob.type, [cachedAtHeader]: String(Date.now()) },
    }),
  )
  const keys = await cache.keys()
  await Promise.all(
    keys.slice(0, Math.max(0, keys.length - diskLimit)).map((key) => cache.delete(key)),
  )
}

async function decodeIcon(source: string): Promise<HTMLCanvasElement> {
  const image = new Image()
  image.referrerPolicy = 'no-referrer'
  image.src = source
  await image.decode()

  const thumbnail = document.createElement('canvas')
  thumbnail.width = thumbnail.height = suggestionIconPixels
  const context = thumbnail.getContext('2d')!
  const scale = suggestionIconPixels / Math.max(image.naturalWidth, image.naturalHeight)
  const width = image.naturalWidth * scale
  const height = image.naturalHeight * scale
  context.drawImage(
    image,
    (suggestionIconPixels - width) / 2,
    (suggestionIconPixels - height) / 2,
    width,
    height,
  )
  return thumbnail
}

function centerIconArtwork(thumbnail: HTMLCanvasElement): void {
  const { width, height } = thumbnail
  const context = thumbnail.getContext('2d')!
  const pixels = context.getImageData(0, 0, width, height)
  let left = width
  let right = -1
  let top = height
  let bottom = -1

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (pixels.data[(y * width + x) * 4 + 3] === 0) continue
      left = Math.min(left, x)
      right = Math.max(right, x)
      top = Math.min(top, y)
      bottom = Math.max(bottom, y)
    }
  }
  if (right < 0) return

  const offsetX = Math.round((width - 1 - left - right) / 2)
  const offsetY = Math.round((height - 1 - top - bottom) / 2)
  if (offsetX === 0 && offsetY === 0) return

  context.clearRect(0, 0, width, height)
  context.putImageData(pixels, offsetX, offsetY)
}

async function decodeBlob(blob: Blob): Promise<HTMLCanvasElement> {
  const source = URL.createObjectURL(blob)
  try {
    const thumbnail = await decodeIcon(source)
    centerIconArtwork(thumbnail)
    return thumbnail
  } finally {
    URL.revokeObjectURL(source)
  }
}

async function loadIcon(url: string): Promise<HTMLCanvasElement | undefined> {
  const cache = await openDiskCache()
  try {
    const cached = await cache?.match(url)
    const cachedAt = Number(cached?.headers.get(cachedAtHeader))
    if (cached && Date.now() - cachedAt < maxDiskAge) return await decodeBlob(await cached.blob())
  } catch {
    // A failed disk read can be recovered from the source URL.
  }

  let response: Response
  try {
    response = await fetch(url, { credentials: 'omit', referrerPolicy: 'no-referrer' })
  } catch {
    // Images can still be displayed if the media server rejects a CORS request.
    return decodeIcon(url)
  }
  if (!response.ok) return undefined

  const blob = await response.blob()
  const icon = await decodeBlob(blob)
  if (cache) void storeIcon(cache, url, blob).catch(() => {})
  return icon
}

function loadQueuedIcons(): void {
  for (const [url, request] of requests) {
    if (activeLoads >= parallelLoads) break
    if (request.started) continue

    request.started = true
    activeLoads++
    void loadIcon(url)
      .catch(() => undefined)
      .then((icon) => {
        if (!icon) return
        thumbnails.set(url, icon)
        if (thumbnails.size > memoryLimit) thumbnails.delete(thumbnails.keys().next().value!)
        for (const paint of request.listeners) paint(icon)
      })
      .finally(() => {
        activeLoads--
        requests.delete(url)
        loadQueuedIcons()
      })
  }
}

export function paintSuggestionIcon(url: string, paint: PaintIcon): (() => void) | undefined {
  const cached = thumbnails.get(url)
  if (cached) {
    thumbnails.delete(url)
    thumbnails.set(url, cached)
    paint(cached)
    return
  }

  const request = requests.get(url) ?? { started: false, listeners: new Set<PaintIcon>() }
  request.listeners.add(paint)
  requests.set(url, request)
  loadQueuedIcons()
  return () => {
    request.listeners.delete(paint)
    if (!request.started && request.listeners.size === 0) requests.delete(url)
  }
}
