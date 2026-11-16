<script setup lang="ts">
import { computed, onBeforeUnmount, ref, onMounted } from "vue";
import { useRouter ,useRoute } from "vue-router";
import { Mute, Microphone, Promotion, RefreshRight } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import {
  getVoiceCurrentTurn,
  submitVoiceTurn,
  uploadVoiceAudio,
  getVoiceSession,
  type VoiceTurnVO,
  type VoiceSessionVO,
} from "@/api/business.ts";

const router = useRouter()
const route = useRoute()
// ---------- 页面状态 ----------
const inputText = ref('')
const interimText = ref('')
const isListening = ref(false)
const submitting = ref(false)
const isGenerating = ref(false) // 出题中（QUEUED/PROCESSING）

// AI 回复相关状态
const aiReplyText = ref('') // 后端返回的问题文本
const questionAudioUrl = ref('')
const currentTurn = ref<VoiceTurnVO | null>(null) // 后端返回的回合数据
const sessionInfo = ref<VoiceSessionVO | null>(null) // 当前会话
const sessionId = ref('') // 当前会话 id
const isPlayingAudio = ref(false) // 控制音浪特效
const isFinished = ref(false) // 是否已全部答完（播放结尾语）
const currentAudio = ref<HTMLAudioElement | null>(null)

// 动态获取进度条
const progressPercent = computed(() => {
  const total = sessionInfo.value?.totalTurns || 0
  if (total === 0) return 0
  const current = currentTurn.value?.turnIndex ?? 0
  return Math.min(Math.round((current / total) * 100), 100)
})
let delayTimer: ReturnType<typeof setTimeout> | null = null
let typewriterTimer: ReturnType<typeof setInterval> | null = null
let pollTimer: ReturnType<typeof setTimeout> | null = null

// ---------- 录音相关（WAV / PCM） ----------
let audioContext: AudioContext | null = null
let mediaStream: MediaStream | null = null
let scriptProcessor: ScriptProcessorNode | null = null
let sourceNode: MediaStreamAudioSourceNode | null = null
let pcmChunks: Float32Array[] = []
let recordingSampleRate = 16000
let recordedBlob: Blob | null = null // 已录制完成的音频，等待提交

onMounted(async () => {
  try {
    sessionId.value = route.params.sessionId as string
    currentTurn.value = await getVoiceCurrentTurn(sessionId.value)
    sessionInfo.value = await getVoiceSession(sessionId.value)
    playText(currentTurn.value?.questionContent || '')
    startReply()
  } catch (error) {
    console.error(error)
    ElMessage.error('语音面试会话初始化失败')
  }
})

// ---------- 打字机 ----------
const playText = (fullText: string) => {
  if (delayTimer) clearTimeout(delayTimer)
  if (typewriterTimer) clearInterval(typewriterTimer)
  aiReplyText.value = ''
  delayTimer = window.setTimeout(() => {
    let index = 0
    typewriterTimer = window.setInterval(() => {
      if (index < fullText.length) {
        aiReplyText.value += fullText.charAt(index)
        index++
      } else if (typewriterTimer) {
        clearInterval(typewriterTimer)
      }
    }, 80)
  }, 300)
}

// ---------- 音频播放（单实例 + 播放状态） ----------
const startReply = () => {
  const audioUrl = currentTurn.value?.questionAudioUrl
  if (!audioUrl) {
    ElMessage.warning('当前没有问题音频')
    return
  }
  questionAudioUrl.value = audioUrl
  currentAudio.value?.pause()
  const audio = new Audio(audioUrl)
  currentAudio.value = audio
  audio.onplay = () => { isPlayingAudio.value = true }
  audio.onended = () => { isPlayingAudio.value = false }
  audio.onerror = () => { isPlayingAudio.value = false }
  audio.play().catch(err => {
    isPlayingAudio.value = false
    console.error('播放失败:', err)
    ElMessage.warning('请点击"重新播放"按钮重试')
  })
}

// ---------- 录音控制 ----------
const toggleListening = async () => {
  if (isListening.value) {
    recordedBlob = await stopRecording()
    if (recordedBlob) {
      inputText.value = '已录音，点击"发送"提交'
    }
  } else {
    recordedBlob = null
    await startRecording()
  }
}

const startRecording = async () => {
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    // 优先 16kHz（Qwen ASR 采样率）；不支持的浏览器回退默认采样率，实际值内嵌在 WAV 头
    try {
      audioContext = new AudioContext({ sampleRate: 16000 })
    } catch {
      audioContext = new AudioContext()
    }
    recordingSampleRate = audioContext.sampleRate
    if (audioContext.state === 'suspended') {
      await audioContext.resume()
    }
    sourceNode = audioContext.createMediaStreamSource(mediaStream)
    scriptProcessor = audioContext.createScriptProcessor(4096, 1, 1)
    pcmChunks = []
    scriptProcessor.onaudioprocess = (e) => {
      pcmChunks.push(new Float32Array(e.inputBuffer.getChannelData(0)))
    }
    // 通过静音 gain 节点保持音频图活跃，同时避免麦克风回放产生回声
    const gain = audioContext.createGain()
    gain.gain.value = 0
    sourceNode.connect(scriptProcessor)
    scriptProcessor.connect(gain)
    gain.connect(audioContext.destination)
    isListening.value = true
    inputText.value = ''
    interimText.value = ''
  } catch (e) {
    console.error(e)
    ElMessage.error('无法访问麦克风，请检查权限')
  }
}

const stopRecording = (): Promise<Blob | null> => {
  return new Promise((resolve) => {
    if (!scriptProcessor || !audioContext) {
      isListening.value = false
      resolve(null)
      return
    }
    scriptProcessor.onaudioprocess = null
    scriptProcessor.disconnect()
    sourceNode?.disconnect()
    mediaStream?.getTracks().forEach(t => t.stop())
    mediaStream = null
    audioContext.close()
    audioContext = null
    scriptProcessor = null
    sourceNode = null
    isListening.value = false
    if (pcmChunks.length === 0) {
      resolve(null)
      return
    }
    const wav = encodeWav(pcmChunks, recordingSampleRate)
    resolve(new Blob([wav], { type: 'audio/wav' }))
  })
}

// 16-bit 单声道 PCM -> WAV
function encodeWav(chunks: Float32Array[], sampleRate: number): ArrayBuffer {
  const numChannels = 1
  const bytesPerSample = 2
  const totalSamples = chunks.reduce((n, c) => n + c.length, 0)
  const buffer = new ArrayBuffer(44 + totalSamples * numChannels * bytesPerSample)
  const view = new DataView(buffer)

  const writeString = (offset: number, str: string) => {
    for (let i = 0; i < str.length; i++) {
      view.setUint8(offset + i, str.charCodeAt(i))
    }
  }

  writeString(0, 'RIFF')
  view.setUint32(4, 36 + totalSamples * numChannels * bytesPerSample, true)
  writeString(8, 'WAVE')
  writeString(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, numChannels, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * numChannels * bytesPerSample, true)
  view.setUint16(32, numChannels * bytesPerSample, true)
  view.setUint16(34, 16, true)
  writeString(36, 'data')
  view.setUint32(40, totalSamples * numChannels * bytesPerSample, true)

  let offset = 44
  for (const chunk of chunks) {
    for (let i = 0; i < chunk.length; i++) {
      const s = Math.max(-1, Math.min(1, chunk[i]))
      view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true)
      offset += 2
    }
  }
  return buffer
}

// ---------- 提交 + 轮询 ----------
const handleSend = async () => {
  if (submitting.value) return
  if (!currentTurn.value) {
    ElMessage.warning('当前没有题目')
    return
  }
  // 若还在录音，先停止拿到音频；否则用上次已录好的音频
  let blob = recordedBlob
  if (isListening.value) {
    blob = await stopRecording()
    recordedBlob = blob
  }
  if (!blob || blob.size === 0) {
    ElMessage.warning('请先点击麦克风录音')
    return
  }
  submitting.value = true
  try {
    const key = await uploadVoiceAudio(blob, 'answer.wav')
    const vo = await submitVoiceTurn({
      sessionId: sessionId.value,
      userAudioKey: key,
      turnIndex: currentTurn.value.turnIndex,
    })
    sessionInfo.value = vo
    recordedBlob = null
    inputText.value = ''
    handleSubmitResult(vo)
  } catch (e) {
    console.error(e)
    ElMessage.error('提交失败，请重试')
  } finally {
    submitting.value = false
  }
}

const handleSubmitResult = (vo: VoiceSessionVO) => {
  if (vo.sessionStatus === 'FINISHED') {
    playOutroAndLeave(vo)
    return
  }
  const status = vo.questionGenStatus
  if (status === 'QUEUED' || status === 'PROCESSING') {
    isGenerating.value = true
    pollUntilReady()
  } else if (status === 'COMPLETED') {
    isGenerating.value = false
    fetchAndPlayCurrentTurn()
  } else if (status === 'FAILED') {
    isGenerating.value = false
    ElMessage.error('出题失败，请重试')
  }
}

const pollUntilReady = () => {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = window.setTimeout(async () => {
    try {
      const vo = await getVoiceSession(sessionId.value)
      sessionInfo.value = vo
      if (vo.sessionStatus === 'FINISHED') {
        isGenerating.value = false
        playOutroAndLeave(vo)
        return
      }
      const status = vo.questionGenStatus
      if (status === 'COMPLETED') {
        isGenerating.value = false
        await fetchAndPlayCurrentTurn()
      } else if (status === 'FAILED') {
        isGenerating.value = false
        ElMessage.error('出题失败，请重试')
      } else {
        pollUntilReady()
      }
    } catch (e) {
      console.error(e)
      pollUntilReady()
    }
  }, 2000)
}

const fetchAndPlayCurrentTurn = async () => {
  const turn = await getVoiceCurrentTurn(sessionId.value)
  currentTurn.value = turn
  playText(turn.questionContent || '')
  startReply()
}

const goToRecord = () => {
  router.push({ name: 'record' })
}

// 播放结语音频，结束后跳转到面试记录页
const playOutroAndLeave = (vo: VoiceSessionVO) => {
  isFinished.value = true
  isGenerating.value = false
  const outroUrl = vo.outroAudioUrl
  if (!outroUrl) {
    goToRecord()
    return
  }
  currentAudio.value?.pause()
  const audio = new Audio(outroUrl)
  currentAudio.value = audio
  audio.onended = () => goToRecord()
  audio.onerror = () => goToRecord()
  audio.play().catch(() => goToRecord())
}

onBeforeUnmount(() => {
  if (delayTimer) clearTimeout(delayTimer)
  if (typewriterTimer) clearInterval(typewriterTimer)
  if (pollTimer) clearTimeout(pollTimer)
  scriptProcessor?.disconnect()
  sourceNode?.disconnect()
  mediaStream?.getTracks().forEach(t => t.stop())
  audioContext?.close()
  currentAudio.value?.pause()
})

</script>

<template>
  <div class="voice-interview-container">
    <!-- 顶部标题 -->
    <div class="voice-header">
      <el-icon class="header-icon"><Microphone /></el-icon>
      <span class="header-text">AI 语音面试</span>
    </div>

    <!-- 进度条 (参照 interviewExam) -->
    <div class="exam-progress">
      <div>题目 {{ currentTurn?.turnIndex || 0 }} / {{ sessionInfo?.totalTurns || 0 }}</div>
      <el-progress
          :percentage="progressPercent"
          :show-text="false"
          color="#626aef"
          :stroke-width="8"
      />
    </div>

    <!-- 中间对话/机器人展示区 -->
    <div class="voice-card">
      <div class="bot-center-box">
        <div class="bot-wrapper">
          <!-- 脉冲波纹特效 (播放音频时触发) -->
          <div class="pulse-ring" :class="{ 'is-active': isPlayingAudio }"></div>
          <div class="pulse-ring ring-2" :class="{ 'is-active': isPlayingAudio }"></div>

          <!-- 机器人本体 -->
          <div class="bot-avatar">
            <svg class="bot-icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" width="1em" height="1em">
              <path d="M 512 200 L 512 270" fill="none" stroke="currentColor" stroke-width="65" stroke-linecap="round"></path>
              <circle cx="512" cy="180" r="45" fill="currentColor"></circle>
              <path d="M 180 520 L 230 520" fill="none" stroke="currentColor" stroke-width="70" stroke-linecap="round"></path>
              <path d="M 794 520 L 844 520" fill="none" stroke="currentColor" stroke-width="70" stroke-linecap="round"></path>
              <rect x="230" y="340" width="564" height="360" rx="120" fill="none" stroke="currentColor" stroke-width="70"></rect>
              <circle cx="420" cy="520" r="38" fill="currentColor"></circle>
              <circle cx="604" cy="520" r="38" fill="currentColor"></circle>
            </svg>
          </div>

          <!-- 重新播放按钮 -->
          <el-button
              class="replay-btn"
              size="small"
              round
              :icon="RefreshRight"
              @click="startReply"
          >
            重新播放
          </el-button>
        </div>

        <!-- 动态状态提示文字 -->
        <div class="status-text">
          {{ isPlayingAudio ? 'AI 正在播报...' : (isGenerating ? 'AI 正在出题...' : (isListening ? '正在聆听...' : '等待输入')) }}
        </div>

        <!-- AI 回复的文字版展示区 -->
        <div class="voice-interviewer-message" v-if="aiReplyText">
          <div class="message-bubble">
            {{ aiReplyText }}
          </div>
        </div>
      </div>
    </div>

    <!-- 底部输入控制区：全部答完后隐藏 -->
    <div class="voice-user-input" v-if="!isFinished">
      <!-- 左侧：麦克风切换按钮 -->
      <div class="mic-toggle" @click="toggleListening" :class="{ 'is-recording': isListening }">
        <el-icon v-if="isListening" class="mic-icon active"><Microphone /></el-icon>
        <el-icon v-else class="mic-icon muted"><Mute /></el-icon>
      </div>

      <!-- 中间：展示识别结果 -->
      <div class="input-display">
        <!-- 正在说话时的中间结果 (灰色) -->
        <span v-if="interimText" class="interim-text">{{ interimText }}</span>
        <!-- 最终识别的文本 (深色) -->
        <span v-else-if="inputText" class="final-text">{{ inputText }}</span>
        <!-- 占位符 -->
        <span v-else class="placeholder-text">
          {{ isListening ? '请开始说话...' : '点击左侧麦克风开始说话' }}
        </span>
      </div>

      <!-- 右侧：提交按钮 -->
      <el-button
          type="primary"
          class="submit-btn"
          :icon="Promotion"
          :loading="submitting"
          @click="handleSend"
      >
        发送
      </el-button>
    </div>
  </div>
</template>


<style scoped>
/* 页面整体容器 */
.voice-interview-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background-color: #F5F7FA;
  gap: 24px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

/* 顶部 Header */
.voice-header {
  display: flex;
  gap: 16px;
  align-items: center;
}
.header-icon {
  padding: 12px;
  background: linear-gradient(135deg, #626aef, #8b93ff);
  font-size: 28px;
  color: #ffffff;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(98, 106, 239, 0.3);
}
.header-text {
  font-size: 32px;
  color: #1f2d3d;
  font-weight: 600;
  margin: 0;
  letter-spacing: 1px;
}

/* 中间核心卡片 */
.voice-card {
  background-color: #ffffff;
  border-radius: 24px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  width: 100%;
  max-width: 800px;
  height: 520px;
  box-sizing: border-box;
  padding: 40px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
  position: relative;
}

/* 进度条 */
.exam-progress {
  background-color: #ffffff;
  width: 100%;
  max-width: 800px;
  padding:10px;
  border-radius: 12px;
  box-sizing: border-box;
}
.exam-progress > div:first-child {
  font-size: 14px;
  color: #8c9bb4;
  margin-bottom: 8px;
  font-weight: 500;
}

/* 重新播放按钮 */
.replay-btn {
  position: absolute;
  bottom: -18px;
  z-index: 20;
  background-color: #ffffff;
  color: #626aef;
  border: 1px solid #c1c8ff;
  box-shadow: 0 2px 8px rgba(98, 106, 239, 0.15);
}
.replay-btn:hover {
  background-color: #f0f2ff;
  color: #626aef;
  border-color: #626aef;
}

/* 机器人居中容器 */
.bot-center-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding-top: 20px;
}

/* 机器人容器与脉冲动画 */
.bot-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 160px;
  height: 160px;
  margin-bottom: 20px;
}

/* 机器人本体 */
.bot-avatar {
  width: 130px;
  height: 130px;
  background-color: #EBF0FF;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 80px;
  color: #626aef;
  position: relative;
  z-index: 10;
  transition: all 0.3s ease;
}

/* 脉冲波纹特效 */
.pulse-ring {
  position: absolute;
  width: 130px;
  height: 130px;
  border-radius: 50%;
  background-color: rgba(98, 106, 239, 0.25);
  opacity: 0;
  z-index: 1;
}
.pulse-ring.is-active {
  animation: pulse-animation 1.8s infinite cubic-bezier(0.215, 0.61, 0.355, 1);
}
.pulse-ring.ring-2.is-active {
  animation-delay: 0.9s;
}
@keyframes pulse-animation {
  0% { transform: scale(1); opacity: 0.8; }
  100% { transform: scale(2.2); opacity: 0; }
}

/* 状态文字 */
.status-text {
  font-size: 16px;
  color: #8c9bb4;
  margin-top: 10px;
  margin-bottom: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
  height: 24px;
}

/* AI 文字版气泡样式 */
.voice-interviewer-message {
  width: 100%;
  max-width: 600px;
  display: flex;
  justify-content: center;
  max-height: 150px;
  overflow-y: auto;
}
.voice-interviewer-message::-webkit-scrollbar {
  display: none;
}
.message-bubble {
  background-color: #F7F9FC;
  color: #4A5568;
  font-size: 16px;
  line-height: 1.6;
  padding: 12px 24px;
  border-radius: 16px;
  border: 1px solid #EBF0FF;
  text-align: left;
  animation: fadeIn 0.3s ease-in-out;
  width: fit-content;
  max-width: 100%;
  word-break: break-all;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 底部输入控制区 */
.voice-user-input {
  background-color: #ffffff;
  width: 100%;
  max-width: 800px;
  border-radius: 20px;
  padding: 16px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border: 1px solid #edf2f7;
  box-sizing: border-box;
}

/* 麦克风按钮 */
.mic-toggle {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background-color: #F2F5FA;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}
.mic-toggle:hover {
  background-color: #E5EAF4;
}
.mic-toggle.is-recording {
  background-color: #fee2e2; /* 录音时背景变浅红 */
}
.mic-icon {
  font-size: 22px;
}
.mic-icon.active {
  color: #ef4444; /* 录音时麦克风变红 */
}
.mic-icon.muted {
  color: #a0aab8;
}

/* 识别结果显示区 */
.input-display {
  flex: 1;
  font-size: 15px;
  line-height: 1.5;
  min-height: 22px;
  display: flex;
  align-items: center;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.interim-text {
  color: #a0aab8; /* 中间识别结果用浅色 */
}
.final-text {
  color: #1f2d3d; /* 最终结果用深色 */
  font-weight: 500;
}
.placeholder-text {
  color: #c0c4cc;
  user-select: none;
}

/* 发送按钮 */
.submit-btn {
  border-radius: 12px;
  padding: 10px 24px;
  font-weight: 500;
  font-size: 15px;
  background-color: #626aef;
  border-color: #626aef;
  transition: all 0.2s;
  flex-shrink: 0;
}
.submit-btn:hover {
  background-color: #7b82f3;
  border-color: #7b82f3;
  transform: translateY(-1px);
}
</style>