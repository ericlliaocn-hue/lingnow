<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { 
  Sparkles, Code2, Box, Monitor, Tablet, Smartphone, Layout, Layers, Terminal, LogOut, Zap, Send, PanelRight, Fullscreen, Maximize2, Loader2, Plus, Globe, History, ChevronRight
} from 'lucide-vue-next'
import { Sandpack } from 'sandpack-vue3'

const prompt = ref('')
const loading = ref(false)
const result = ref(null)
const error = ref(null)
const activeTab = ref('design') // 默认进入设计视图

/**
 * i18n & History
 */
const locale = ref(localStorage.getItem('lingnow_lang') || 'ZH')
const history = ref([])
const isHistoryOpen = ref(false)

const i18n = computed(() => ({
  ZH: {
    welcome: '你想建造什么？',
    subtitle: '告诉数字军团你的创意，我们将实时为您构建全栈应用。',
    placeholder: '在这里描述您的增量需求或新愿景...',
    action: '开启任务',
    tab_canvas: '视觉画板',
    tab_logic: '逻辑矩阵',
    tab_source: '源码视图',
    tab_sandbox: '实时沙盒',
    history: '历史记录',
    planning: '需求分析',
    designing: '视觉对标',
    coding: '代码合成',
    done: '资产交付',
    loading_subtitle: '正在调度全球算力，请稍候...',
    new_project: '开启新灵感',
    history_title: '历史记录',
    history_empty: '暂无存档记录',
    switch_lang: 'English'
  },
  EN: {
    welcome: 'What do you want to build?',
    subtitle: 'Tell the digital legion your ideas, we build full-stack apps in real-time.',
    placeholder: 'Describe your incremental redesign or new vision here...',
    action: 'Generate',
    tab_canvas: 'Canvas',
    tab_logic: 'Logic',
    tab_source: 'Source',
    tab_sandbox: 'Sandbox',
    history: 'History',
    planning: 'Planning',
    designing: 'Designing',
    coding: 'Coding',
    done: 'Done',
    loading_subtitle: 'Orchestrating worldwide computing power...',
    new_project: 'New Project',
    history_title: 'Project Archive',
    history_empty: 'No archives yet',
    switch_lang: '中文'
  }
}[locale.value]))

const toggleLang = () => {
  locale.value = locale.value === 'ZH' ? 'EN' : 'ZH'
  localStorage.setItem('lingnow_lang', locale.value)
}

const fetchHistory = async () => {
  try {
    const res = await axios.get('/api/projects/all')
    history.value = res.data
  } catch (err) {
    console.error('Failed to fetch history', err)
  }
}

const loadProject = async (id) => {
  loading.value = true
  try {
    const res = await axios.get(`/api/projects/${id}`)
    result.value = res.data
    currentSessionId.value = id
    isHistoryOpen.value = false
    activeTab.value = 'design'
  } catch (err) {
    error.value = '加载项目失败'
  } finally {
    loading.value = false
  }
}

/**
 * Workbench Mode Logic
 */
const isWorkbenchMode = computed(() => !!result.value && !error.value)
const isSidebarOpen = ref(true)
const deviceType = ref('desktop') // 'desktop' | 'tablet' | 'mobile'

/**
 * Computed files for Sandpack
 */
const sandpackFiles = computed(() => {
  if (!result.value || !result.value.files) return {}
  const files = {}
  Object.entries(result.value.files).forEach(([path, content]) => {
    const cleanPath = path.startsWith('/') ? path.substring(1) : path
    files[cleanPath] = content
  })
  return files
})

const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
const authData = ref({ username: 'eric', password: 'anbs,23t' })
const isLoginMode = ref(true)

// Axios configuration
axios.interceptors.request.use(config => {
  if (user.value && user.value.token) {
    config.headers['satoken'] = user.value.token
  }
  return config
}, error => Promise.reject(error))

const handleAuth = async () => {
  if (!authData.value.username || !authData.value.password) return
  loading.value = true
  try {
    const endpoint = isLoginMode.value ? '/api/auth/login' : '/api/auth/register'
    const res = await axios.post(endpoint, authData.value)
    user.value = res.data
    localStorage.setItem('user', JSON.stringify(user.value))
    axios.defaults.headers.common['satoken'] = res.data.token
    window.location.reload()
  } catch (err) {
    error.value = '认证失败，请检查用户名或密码'
  } finally {
    loading.value = false
  }
}

const handleLogout = () => {
  user.value = null
  localStorage.removeItem('user')
  window.location.reload()
}

/**
 * M6: State Recovery & Persistence
 */
onMounted(async () => {
  if (!user.value) return
  
  const lastSessionId = localStorage.getItem('lastSessionId')
  if (lastSessionId) {
    loading.value = true
    try {
      const res = await axios.get(`/api/projects/${lastSessionId}`)
      if (res.data && res.data.prototypeHtml) {
        result.value = res.data
        currentSessionId.value = lastSessionId
      }
    } catch (err) {
      console.warn('Failed to restore session', err)
    } finally {
      loading.value = false
    }
  }
})

const currentSessionId = ref(`session-${Date.now()}`)
const generationPhase = ref('idle') 

const handleGenerate = async () => {
  if (!prompt.value.trim() || loading.value) return
  
  loading.value = true
  error.value = null
  
  // Detection for Iterative Redesign Mode
  const isRedesignMode = !!(result.value && result.value.prototypeHtml);
  
  if (isRedesignMode) {
    generationPhase.value = 'designing'
    try {
      const res = await axios.post('/api/generate/redesign', {
        sessionId: currentSessionId.value,
        prompt: prompt.value
      }, { timeout: 1800000 })
      
      result.value = res.data
      prompt.value = ''
      generationPhase.value = 'done'
    } catch (err) {
      error.value = err.response?.data?.description || err.message || '迭演修改失败'
      generationPhase.value = 'done'
    } finally {
      loading.value = false
    }
    return
  }

  // Standard Initial Generation Flow
  generationPhase.value = 'planning'
  try {
    const planRes = await axios.post('/api/generate/plan', {
      prompt: prompt.value,
      sessionId: currentSessionId.value
    }, { timeout: 1800000 })
    
    result.value = planRes.data
    generationPhase.value = 'designing'

    const designRes = await axios.post('/api/generate/design', {
      sessionId: currentSessionId.value,
      lang: locale.value // Pass language context to AI
    }, { timeout: 1800000 })

    result.value = designRes.data
    generationPhase.value = 'done'
    activeTab.value = 'design'
    prompt.value = ''
    
    // Perspective Persistence: Save session to local storage
    localStorage.setItem('lastSessionId', currentSessionId.value)
    fetchHistory() // Refresh history list
  } catch (err) {
    error.value = err.response?.data?.description || err.message || '生成中断，请检查设置'
    generationPhase.value = 'idle'
  } finally {
    loading.value = false
  }
}

const handleConfirm = async () => {
  loading.value = true
  error.value = null
  generationPhase.value = 'developing'
  try {
    const response = await axios.post('/api/generate/develop', {
      sessionId: currentSessionId.value
    }, { timeout: 1800000 })
    result.value = response.data
    generationPhase.value = 'done'
    activeTab.value = 'preview'
  } catch (err) {
    error.value = err.response?.data?.description || err.message || '代码生成失败，请重试'
    generationPhase.value = 'awaiting_confirmation'
  } finally {
    loading.value = false
  }
}

const isPastState = (state) => {
  const states = ['PLANNING', 'DESIGNING', 'CODING', 'DONE']
  const currentStatus = result.value?.status || 'PLANNING'
  return states.indexOf(state) < states.indexOf(currentStatus)
}

const resetProject = () => {
  prompt.value = ''
  result.value = null
  error.value = null
  generationPhase.value = 'idle'
  currentSessionId.value = `session-${Date.now()}`
  activeTab.value = 'design'
}
</script>

<template>
  <div class="h-full flex flex-col bg-black text-gray-300 select-none overflow-hidden">
    <!-- Top Global Navigation -->
    <nav class="h-16 flex items-center justify-between px-8 border-b border-white/5 bg-black/40 backdrop-blur-3xl z-40 shrink-0">
      <div class="flex items-center gap-6">
        <div @click="resetProject" class="flex items-center gap-3 cursor-pointer group">
          <div class="w-10 h-10 bg-gradient-to-br from-blue-600 to-indigo-700 rounded-xl flex items-center justify-center shadow-lg group-hover:shadow-blue-500/20 transition-all">
            <Zap class="w-6 h-6 text-white fill-current" />
          </div>
          <h1 class="text-xl font-black text-white tracking-tighter uppercase italic">LingNow</h1>
        </div>
        
        <!-- Mode Tabs -->
        <div v-if="isWorkbenchMode" class="flex bg-white/5 p-1 rounded-xl border border-white/5 ml-4">
          <button 
            v-for="t in [
              {id: 'design', icon: Layout, label: i18n.tab_canvas},
              {id: 'plan', icon: Layers, label: i18n.tab_logic},
              {id: 'code', icon: Terminal, label: i18n.tab_source},
              {id: 'preview', icon: Box, label: i18n.tab_sandbox}
            ]"
            :key="t.id"
            @click="activeTab = t.id"
            :class="[activeTab === t.id ? 'bg-white/10 text-white shadow-lg' : 'text-gray-500 hover:text-gray-300']"
            class="flex items-center gap-2 px-4 py-1.5 rounded-lg text-xs font-bold transition-all"
          >
            <component :is="t.icon" class="w-3.5 h-3.5" />
            {{ t.label }}
          </button>
        </div>
      </div>

      <div class="flex items-center gap-4">
        <!-- History Trigger (Moved to Right) -->
        <button 
          @click="isHistoryOpen = !isHistoryOpen; if(isHistoryOpen) fetchHistory()"
          class="flex items-center gap-2 px-3 py-2 rounded-xl bg-white/5 border border-white/5 hover:border-white/20 text-gray-400 hover:text-white transition-all group"
          :title="i18n.history"
        >
          <History class="w-4 h-4 group-hover:text-blue-400 transition-colors" />
          <span class="text-[10px] font-black uppercase tracking-widest">{{ i18n.history }}</span>
        </button>

        <!-- Language Switcher -->
        <button 
          @click="toggleLang"
          class="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-white/5 border border-white/5 hover:border-white/20 transition-all group"
        >
          <Globe class="w-3.5 h-3.5 text-gray-500 group-hover:text-blue-400 transition-colors" />
          <span class="text-[10px] font-black text-gray-400 group-hover:text-white">{{ i18n.switch_lang }}</span>
        </button>

        <div v-if="user" class="flex items-center gap-3 px-4 py-1.5 rounded-xl bg-white/5 border border-white/5">
          <div class="text-right">
             <p class="text-[10px] font-black text-blue-400 uppercase tracking-widest leading-none">Developer</p>
             <p class="text-xs font-bold text-gray-200 mt-0.5">{{ user.username }}</p>
          </div>
          <button @click="handleLogout" class="text-gray-600 hover:text-white transition-colors ml-2"><LogOut class="w-4 h-4" /></button>
        </div>
      </div>
    </nav>

    <!-- Main Workspace -->
    <div class="flex-1 flex overflow-hidden relative">
      
      <!-- LEFT: Canvas Area (Prototype/Preview/Code) -->
      <main 
        class="relative transition-all duration-500 ease-in-out overflow-hidden flex flex-col bg-[#111]"
        :class="[isWorkbenchMode ? (isSidebarOpen ? 'w-[75%]' : 'w-full') : 'w-full flex items-center justify-center']"
      >
        <!-- Initial Welcome Screen -->
        <div v-if="!isWorkbenchMode && !loading" class="max-w-2xl w-full px-6 text-center animate-in fade-in zoom-in duration-1000">
          <div class="w-24 h-24 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-[2rem] mx-auto mb-8 shadow-2xl flex items-center justify-center rotate-6 scale-110">
             <Sparkles class="w-12 h-12 text-white" />
          </div>
          <h2 class="text-5xl font-black mb-4 tracking-tighter text-white">{{ i18n.welcome }}</h2>
          <p class="text-gray-500 text-lg mb-12">{{ i18n.subtitle }}</p>
          
          <div class="relative group max-w-xl mx-auto">
            <textarea 
              v-model="prompt"
              :placeholder="i18n.placeholder"
              class="w-full h-40 bg-white/5 border border-white/10 rounded-3xl p-6 text-lg text-white focus:ring-4 focus:ring-blue-500/20 focus:border-blue-500/50 outline-none transition-all placeholder:text-gray-700 shadow-2xl"
              @keydown.enter.exact.prevent="handleGenerate"
            ></textarea>
            <button 
              @click="handleGenerate"
              :disabled="!prompt.trim() || loading"
              class="absolute bottom-4 right-4 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white px-8 py-3 rounded-2xl font-black flex items-center gap-2 shadow-xl transition-all hover:translate-y-[-2px] active:scale-95"
            >
              <Send class="w-5 h-5" />
              <span>{{ i18n.action }}</span>
            </button>
          </div>
        </div>

        <!-- Workbench Canvas Header (Device Switcher) -->
        <div v-if="isWorkbenchMode" class="h-12 bg-black/40 border-b border-white/5 flex items-center justify-center gap-4 shrink-0 px-4">
           <div class="flex items-center bg-white/5 rounded-lg p-1 border border-white/5 shadow-inner">
              <button 
                v-for="d in [
                  {id: 'desktop', icon: Monitor, label: 'Desktop'},
                  {id: 'tablet', icon: Tablet, label: 'Tablet'},
                  {id: 'mobile', icon: Smartphone, label: 'Mobile'}
                ]"
                :key="d.id"
                @click="deviceType = d.id"
                :class="[deviceType === d.id ? 'bg-blue-600 text-white shadow-lg' : 'text-gray-500 hover:text-gray-300']"
                class="p-1.5 rounded-md transition-all flex items-center gap-2"
              >
                <component :is="d.icon" class="w-4 h-4" />
              </button>
           </div>
           <div class="h-4 w-[1px] bg-white/10 mx-2"></div>
           <span class="text-[10px] font-mono text-gray-500 uppercase tracking-widest">
              Viewport: {{ deviceType === 'desktop' ? 'Fluid' : (deviceType === 'tablet' ? '768px' : '375px') }}
           </span>
        </div>

        <div v-if="isWorkbenchMode" class="flex-1 relative overflow-hidden p-8 flex justify-center bg-[#0d0d0d] scrollbar-hide">
          <transition name="fade" mode="out-in">
            <!-- Prototype View with Mock Device -->
            <div 
              v-if="activeTab === 'design'" 
              :key="'design'"
              class="h-full transition-all duration-500 ease-in-out relative origin-top shadow-[0_0_100px_rgba(0,0,0,0.5)] flex justify-center"
              :style="{ width: deviceType === 'desktop' ? '100%' : (deviceType === 'tablet' ? '768px' : '375px') }"
            >
               <div class="h-full w-full bg-white relative rounded-[2rem] overflow-hidden border border-white/10 group/canvas">
                  <!-- Mock Mobile/Tablet Frame if not desktop -->
                  <div v-if="deviceType !== 'desktop'" class="absolute -inset-4 border-[12px] border-[#1a1a1a] rounded-[3rem] pointer-events-none z-30 shadow-2xl">
                     <div class="absolute top-0 left-1/2 -translate-x-1/2 w-32 h-6 bg-[#1a1a1a] rounded-b-2xl"></div>
                  </div>
                  <iframe 
                    :srcdoc="result?.prototypeHtml"
                    class="w-full h-full border-none relative z-10"
                  ></iframe>
               </div>
            </div>

            <!-- Code Sandbox -->
            <div v-else-if="activeTab === 'preview'" :key="'preview'" class="h-full w-full">
              <Sandpack 
                template="vue3"
                theme="dark"
                :files="sandpackFiles"
                class="sandpack-workbench h-full"
                :options="{
                  editorHeight: '100%',
                  externalResources: ['https://cdn.tailwindcss.com']
                }"
              />
            </div>

            <!-- Source Tree -->
            <div v-else-if="activeTab === 'code'" :key="'code'" class="h-full w-full p-6 overflow-auto bg-[#0a0a0a]">
              <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <div v-for="(content, path) in result?.generatedFiles" :key="path" class="group bg-white/5 border border-white/5 rounded-xl overflow-hidden hover:border-white/20 transition-all">
                  <div class="px-4 py-2 bg-white/5 border-b border-white/5 flex justify-between items-center">
                    <span class="text-[10px] font-mono text-blue-400 opacity-60">{{ path }}</span>
                    <button class="opacity-0 group-hover:opacity-100 transition-opacity"><Code2 class="w-3.5 h-3.5 text-gray-500" /></button>
                  </div>
                  <pre class="p-4 text-[11px] font-mono text-gray-400 overflow-x-auto"><code>{{ content }}</code></pre>
                </div>
              </div>
            </div>

            <!-- Logic View -->
            <div v-else-if="activeTab === 'plan'" :key="'plan'" class="h-full w-full p-12 overflow-auto bg-[#050505]">
               <div class="max-w-4xl mx-auto grid md:grid-cols-2 gap-8">
                  <section class="space-y-6">
                    <h3 class="text-xl font-black text-white flex items-center gap-3">
                      <div class="w-8 h-8 rounded-lg bg-blue-500/20 flex items-center justify-center text-blue-400"><Layers class="w-5 h-5"/></div>
                      功能矩阵
                    </h3>
                    <div v-for="f in result?.features" :key="f.name" class="p-5 rounded-2xl bg-white/5 border border-white/5 hover:bg-white/[0.07] transition-all">
                      <div class="flex justify-between items-start mb-2">
                        <h4 class="font-bold text-white">{{ f.name }}</h4>
                        <span class="text-[9px] px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20 uppercase font-black">P{{ f.priority }}</span>
                      </div>
                      <p class="text-xs text-gray-500 leading-relaxed">{{ f.description }}</p>
                    </div>
                  </section>
                  <section class="space-y-6">
                    <h3 class="text-xl font-black text-white flex items-center gap-3">
                      <div class="w-8 h-8 rounded-lg bg-purple-500/20 flex items-center justify-center text-purple-400"><Layout class="w-5 h-5"/></div>
                      页面图谱
                    </h3>
                    <div v-for="p in result?.pages" :key="p.route" class="flex gap-4 items-start p-4 rounded-xl hover:bg-white/5 transition-all">
                      <div class="text-purple-500 font-mono text-sm pt-0.5">/{{ p.route }}</div>
                      <div class="text-xs text-gray-500">{{ p.description }}</div>
                    </div>
                  </section>
               </div>
            </div>
          </transition>
        </div>

        <!-- Global Loader Overlay -->
        <div v-if="loading" class="absolute inset-0 bg-black/60 backdrop-blur-md z-max flex flex-col items-center justify-center gap-6">
          <div class="relative">
            <Loader2 class="w-16 h-16 text-blue-500 animate-spin" />
            <Zap class="absolute inset-0 m-auto w-6 h-6 text-white animate-pulse" />
          </div>
          <div class="text-center">
            <h3 class="text-xl font-bold tracking-widest text-white uppercase">{{ i18n[generationPhase] || generationPhase }} IN PROGRESS...</h3>
            <p class="text-gray-500 text-sm mt-1 italic">{{ i18n.loading_subtitle }}</p>
          </div>
        </div>
      </main>

      <!-- RIGHT: Copilot Sidebar -->
      <aside 
        v-if="isWorkbenchMode"
        class="border-l border-white/5 bg-black/40 backdrop-blur-3xl flex flex-col transition-all duration-500 overflow-hidden"
        :class="[isSidebarOpen ? 'w-[25%]' : 'w-0 opacity-0 invisible']"
      >
        <!-- Sidebar Header (Status) -->
        <div class="p-6 border-b border-white/5 bg-white/5">
          <div class="flex items-center justify-between mb-6">
            <span class="text-[10px] font-black text-gray-500 uppercase tracking-widest">任务指挥部</span>
            <button @click="isSidebarOpen = false" class="text-gray-600 hover:text-white transition-colors"><PanelRight class="w-4 h-4" /></button>
          </div>
          
          <div class="space-y-4">
            <div v-for="(label, state) in { PLANNING: '需求分析', DESIGNING: '视觉对标', CODING: '代码合成', DONE: '资产交付' }" :key="state" class="flex items-center gap-3">
               <div 
                 :class="[
                   (result?.status === state) ? 'bg-blue-500 scale-125 shadow-blue-500/50' : 
                   (isPastState(state) ? 'bg-green-500 opacity-40' : 'bg-gray-800')
                 ]"
                 class="w-2 h-2 rounded-full transition-all duration-700"
               ></div>
               <span :class="result?.status === state ? 'text-white font-bold' : 'text-gray-600'" class="text-xs uppercase tracking-tighter">{{ label }}</span>
               <Loader2 v-if="result?.status === state" class="w-3 h-3 text-blue-500 animate-spin ml-auto" />
            </div>
          </div>
        </div>

        <!-- Sidebar Actions -->
        <div v-if="generationPhase === 'awaiting_confirmation'" class="p-6 bg-blue-600/10 border-b border-blue-500/20">
           <h4 class="text-sm font-black text-white mb-2 italic">√ 原型已就绪</h4>
           <p class="text-[10px] text-gray-400 mb-4 leading-relaxed">当前展示的是视觉原型草案。如果您满意，请点击下方开启“全量编码”；如需调整，请在此对话。</p>
           <button @click="handleConfirm" class="w-full py-3 bg-white text-black font-black text-xs rounded-xl hover:scale-105 active:scale-95 transition-all shadow-xl flex items-center justify-center gap-2">
              <Zap class="w-4 h-4 fill-current" />
              立即开始编码
           </button>
        </div>

        <!-- Chat / Prompt Area (Anchored to Bottom) -->
        <div class="flex-1 flex flex-col overflow-hidden">
          <div class="flex-1 p-6 overflow-y-auto space-y-4 no-scrollbar">
            <!-- Project Brief -->
            <div v-if="result" class="bg-white/5 rounded-2xl p-4 border border-white/5">
              <span class="text-[9px] font-black text-blue-500 uppercase tracking-widest">{{ locale === 'ZH' ? '当前目标' : 'CURRENT GOAL' }}</span>
              <p class="text-xs text-gray-300 mt-1 leading-relaxed">{{ result.userIntent }}</p>
            </div>
          </div>

          <!-- Bottom Prompt Panel -->
          <div class="p-6 bg-black/60 border-t border-white/5 space-y-3 shrink-0">
            <div class="relative">
              <textarea 
                v-model="prompt"
                :placeholder="i18n.placeholder"
                class="w-full h-24 bg-white/10 border border-white/10 rounded-2xl p-4 text-xs text-white focus:ring-2 focus:ring-blue-500/40 outline-none resize-none no-scrollbar transition-all"
                @keydown.enter.exact.prevent="handleGenerate"
              ></textarea>
              <button 
                @click="handleGenerate"
                :disabled="!prompt.trim() || loading"
                class="absolute bottom-3 right-3 p-2 bg-blue-600 rounded-lg disabled:opacity-50 text-white transition-all scale-90 hover:scale-100 active:scale-95"
              >
                <Send class="w-4 h-4" />
              </button>
            </div>
            <div class="flex justify-between items-center px-1">
              <span class="text-[9px] text-gray-600 italic">由 LingNow AI 全程驱动</span>
              <button @click="resetProject" class="text-[9px] text-gray-400 hover:text-white flex items-center gap-1 transition-colors">
                 <Plus class="w-2.5 h-2.5"/> {{ i18n.new_project }}
              </button>
            </div>
          </div>
        </div>
      </aside>

      <!-- HISTORY DRAWER OVERLAY -->
      <transition name="drawer">
        <div v-if="isHistoryOpen" class="absolute inset-0 z-[100] pointer-events-none">
          <!-- Removed the dim/blur overlay per user request for a cleaner slide-out -->
          <div class="absolute inset-y-0 right-0 w-96 bg-[#0a0a0a]/90 backdrop-blur-2xl border-l border-white/10 shadow-[-50px_0_100px_rgba(0,0,0,0.8)] pointer-events-auto flex flex-col overflow-hidden">
            <div class="p-8 border-b border-white/5 flex items-center justify-between bg-white/5">
              <div class="flex items-center gap-3">
                <History class="w-6 h-6 text-blue-500" />
                <h2 class="text-xl font-black text-white italic tracking-tighter">{{ i18n.history_title }}</h2>
              </div>
              <button @click="isHistoryOpen = false" class="p-2 hover:bg-white/5 rounded-lg text-gray-500 hover:text-white transition-all">
                <ChevronRight class="w-5 h-5" />
              </button>
            </div>
            
            <div class="flex-1 overflow-y-auto p-4 space-y-3 no-scrollbar">
              <div 
                v-for="proj in history" 
                :key="proj.id"
                @click="loadProject(proj.id)"
                class="group p-4 bg-white/5 border border-white/5 rounded-2xl cursor-pointer hover:bg-white/10 hover:border-blue-500/30 transition-all relative overflow-hidden"
              >
                <div class="absolute right-0 top-0 w-1 h-full bg-blue-600 opacity-0 group-hover:opacity-100 transition-all"></div>
                <div class="flex justify-between items-start mb-2">
                   <div class="text-[9px] font-black text-gray-600 uppercase tracking-widest">
                     {{ proj.createdAt > 0 ? new Date(proj.createdAt).toLocaleString() : 'JUST NOW' }}
                   </div>
                   <div class="text-[8px] px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-400 border border-blue-500/20 font-black">{{ proj.version }}</div>
                </div>
                <p class="text-xs text-white font-bold line-clamp-2 leading-relaxed mb-1">{{ proj.userIntent }}</p>
                <div class="flex items-center gap-2">
                   <div class="w-1.5 h-1.5 rounded-full bg-green-500 shadow-[0_0_8px_rgba(34,197,94,0.5)]"></div>
                   <span class="text-[9px] text-gray-500 uppercase font-black tracking-tighter">{{ proj.status }}</span>
                </div>
              </div>
              
              <div v-if="history.length === 0" class="text-center py-20">
                 <History class="w-12 h-12 text-gray-800 mx-auto mb-4" />
                 <p class="text-xs text-gray-600 font-bold uppercase tracking-widest">{{ i18n.history_empty }}</p>
              </div>
            </div>
          </div>
        </div>
      </transition>

      <!-- Sidebar Toggle Badge -->
      <button 
        v-if="isWorkbenchMode && !isSidebarOpen"
        @click="isSidebarOpen = true"
        class="absolute right-6 bottom-6 w-12 h-12 bg-blue-600 rounded-full flex items-center justify-center shadow-2xl hover:scale-110 active:scale-90 transition-all z-50 text-white shadow-blue-500/20"
        title="展开侧边栏"
      >
        <PanelRight class="w-6 h-6 rotate-180" />
      </button>

      <!-- Registration Overlay -->
      <div v-if="!user" class="absolute inset-0 bg-black z-50 flex items-center justify-center p-4">
        <div class="max-w-md w-full bg-gray-900 rounded-3xl p-8 border border-gray-800 shadow-2xl">
          <div class="text-center mb-8">
            <Zap class="w-12 h-12 text-blue-500 mx-auto mb-4" />
            <h2 class="text-2xl font-black text-white italic tracking-tighter">{{ isLoginMode ? '欢迎回来' : '加入数字军团' }}</h2>
            <p class="text-gray-500 text-sm mt-1">登录以访问您的 AI 软件工厂</p>
          </div>
          <div class="space-y-4">
            <div>
              <label class="text-[10px] font-black text-gray-600 uppercase mb-1 block">用户名</label>
              <input v-model="authData.username" type="text" class="w-full bg-black border border-gray-800 rounded-xl px-4 py-3 text-sm text-white focus:ring-2 focus:ring-blue-500 outline-none" />
            </div>
            <div>
              <label class="text-[10px] font-black text-gray-600 uppercase mb-1 block">密码</label>
              <input v-model="authData.password" type="password" class="w-full bg-black border border-gray-800 rounded-xl px-4 py-3 text-sm text-white focus:ring-2 focus:ring-blue-500 outline-none" />
            </div>
            <button @click="handleAuth" :disabled="loading" class="w-full py-4 bg-blue-600 text-white font-black rounded-xl hover:bg-blue-500 transition-all flex items-center justify-center gap-2">
              <Loader2 v-if="loading" class="w-4 h-4 animate-spin" />
              {{ isLoginMode ? '立即登录' : '创建一个' }}
            </button>
            <p class="text-center text-xs text-gray-600 mt-6">
              {{ isLoginMode ? '还没有账户?' : '已经有账户?' }}
              <button @click="isLoginMode = !isLoginMode" class="text-blue-500 font-bold ml-1 hover:underline decoration-2">切换</button>
            </p>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<style>
/* Glassmorphism Logic */
.z-max {
  z-index: 9999;
}

.sandpack-workbench .sp-layout {
  border: none !important;
  border-radius: 0 !important;
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}

.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Drawer Animation: Real Industrial Slide from Right */
.drawer-enter-active,
.drawer-leave-active {
  transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

.drawer-enter-from,
.drawer-leave-to {
  transform: translateX(105%); /* Ensure it hides completely */
  opacity: 0.5;
}

/* Tailwind Base Styles Injection */
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap');

html, body {
  font-family: 'Inter', sans-serif;
  background: black;
  margin: 0;
  overflow: hidden;
}

#app {
  height: 100vh;
}
</style>
