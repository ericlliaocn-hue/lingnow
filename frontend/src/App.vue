<script setup>
import { ref, computed } from 'vue'
import axios from 'axios'
import { 
  Sparkles, Code2, Zap, Send, Loader2, Monitor, Terminal, 
  Box, Info, History, Plus, LogOut, Layout, PanelRight, Fullscreen, Maximize2, Layers
} from 'lucide-vue-next'
import { Sandpack } from 'sandpack-vue3'

const prompt = ref('')
const loading = ref(false)
const result = ref(null)
const error = ref(null)
const activeTab = ref('design') // 默认进入设计视图

/**
 * Workbench Mode Logic
 */
const isWorkbenchMode = computed(() => !!result.value && !error.value)
const isSidebarOpen = ref(true)

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
const authData = ref({ username: '', password: '' })
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
  localStorage.removeItem('user')
  user.value = null
  window.location.reload()
}

const currentSessionId = ref(`session-${Date.now()}`)
const generationPhase = ref('idle') 

const handleGenerate = async () => {
  if (!prompt.value.trim()) {
    error.value = '请输入描述内容'
    return
  }
  loading.value = true
  error.value = null
  generationPhase.value = 'planning'
  
  try {
    const isMod = !!result.value;
    const planRes = await axios.post('/api/generate/plan', {
      prompt: prompt.value,
      sessionId: currentSessionId.value,
      isModification: isMod
    }, { timeout: 1800000 })
    
    result.value = { manifest: planRes.data }
    generationPhase.value = 'designing'

    const designRes = await axios.post('/api/generate/design', {
      sessionId: currentSessionId.value
    }, { timeout: 1800000 })

    result.value = { manifest: designRes.data }
    generationPhase.value = 'awaiting_confirmation'
    if (activeTab.value === 'plan') activeTab.value = 'design'
  } catch (err) {
    error.value = err.response?.data?.description || err.message || '生成失败，请稍后重试'
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
  const currentStatus = result.value?.manifest?.status || 'PLANNING'
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
  <!-- Login Overlay -->
  <div v-if="!user" class="min-h-screen bg-black flex items-center justify-center p-4">
    <div class="max-w-md w-full bg-gray-900 rounded-3xl p-8 border border-gray-800 shadow-2xl">
      <div class="text-center mb-8">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-blue-500/10 text-blue-400 mb-4 border border-blue-500/20">
          <Zap class="w-8 h-8 font-bold"/>
        </div>
        <h2 class="text-2xl font-bold text-white">LingNow 工厂</h2>
        <p class="text-gray-500 text-sm mt-2">身份识别以开启数字军团</p>
      </div>
      <div class="space-y-4">
        <input v-model="authData.username" type="text" placeholder="用户名" class="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-xl text-white focus:ring-2 focus:ring-blue-500 outline-none" />
        <input v-model="authData.password" type="password" placeholder="密码" class="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-xl text-white focus:ring-2 focus:ring-blue-500 outline-none" />
        <button @click="handleAuth" :disabled="loading" class="w-full py-4 bg-gradient-to-r from-blue-500 to-indigo-600 rounded-xl font-bold text-white flex items-center justify-center gap-2 transition-all hover:scale-[1.02]">
          <Loader2 v-if="loading" class="w-5 h-5 animate-spin" />
          <span>{{ isLoginMode ? '进入实验室' : '创建身份' }}</span>
        </button>
        <p class="text-center text-xs text-gray-500">
          <button @click="isLoginMode = !isLoginMode" class="text-blue-400 hover:underline">点击进行{{ isLoginMode ? '注册' : '登录' }}</button>
        </p>
        <div v-if="error" class="p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400 text-xs text-center">{{ error }}</div>
      </div>
    </div>
  </div>

  <div v-else class="h-screen bg-[#050505] text-gray-100 flex flex-col overflow-hidden">
    <!-- Navigation Bar -->
    <nav class="h-16 border-b border-white/5 bg-black/40 backdrop-blur-xl flex items-center justify-between px-6 z-50 shrink-0">
      <div class="flex items-center gap-6">
        <div @click="resetProject" class="flex items-center gap-3 cursor-pointer group">
          <div class="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center group-hover:rotate-12 transition-all">
            <Zap class="w-5 h-5 text-white fill-current" />
          </div>
          <span class="font-black tracking-tighter text-xl">LINGNOW</span>
        </div>
        
        <!-- View Tabs (Visible in Workbench Mode) -->
        <div v-if="isWorkbenchMode" class="flex items-center bg-white/5 rounded-full p-1 ml-4 border border-white/5">
          <button 
            v-for="t in [
              {id: 'design', label: 'Canvas', icon: Layout},
              {id: 'plan', label: 'Logic', icon: Layers},
              {id: 'code', label: 'Source', icon: Terminal},
              {id: 'preview', label: 'Sandbox', icon: Monitor}
            ]"
            :key="t.id"
            @click="activeTab = t.id"
            :class="[activeTab === t.id ? 'bg-white/10 text-white shadow-sm' : 'text-gray-500 hover:text-gray-300']"
            class="px-4 py-1.5 rounded-full text-xs font-bold transition-all flex items-center gap-2"
          >
            <component :is="t.icon" class="w-3.5 h-3.5" />
            {{ t.label }}
          </button>
        </div>
      </div>

      <div class="flex items-center gap-4">
        <div class="flex flex-col items-end mr-2">
            <span class="text-[10px] font-black text-blue-500 uppercase tracking-widest leading-none">Developer</span>
            <span class="text-xs font-medium text-gray-300">{{ user.username }}</span>
        </div>
        <button @click="handleLogout" class="p-2 hover:bg-white/5 rounded-lg transition-colors text-gray-500">
          <LogOut class="w-5 h-5"/>
        </button>
      </div>
    </nav>

    <!-- Main Workspace -->
    <div class="flex-1 flex overflow-hidden relative">
      
      <!-- LEFT: Canvas Area (Prototype/Preview/Code) -->
      <main 
        class="relative transition-all duration-500 ease-in-out overflow-hidden"
        :class="[isWorkbenchMode ? (isSidebarOpen ? 'w-[75%]' : 'w-full') : 'w-full flex items-center justify-center']"
      >
        <!-- Initial Welcome Screen -->
        <div v-if="!isWorkbenchMode && !loading" class="max-w-2xl w-full px-6 text-center animate-in fade-in zoom-in duration-1000">
          <div class="w-24 h-24 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-[2rem] mx-auto mb-8 shadow-2xl flex items-center justify-center rotate-6 scale-110">
             <Sparkles class="w-12 h-12 text-white" />
          </div>
          <h2 class="text-5xl font-black mb-4 tracking-tighter">你想建造什么？</h2>
          <p class="text-gray-500 text-lg mb-12">告诉数字军团你的创意，我们将实时为您构建全栈应用。</p>
          
          <div class="relative group max-w-xl mx-auto">
            <textarea 
              v-model="prompt"
              placeholder="我想做一个宠物社交 APP，用户可以晒自家宠物..."
              class="w-full h-40 bg-white/5 border border-white/10 rounded-3xl p-6 text-lg focus:ring-4 focus:ring-blue-500/20 focus:border-blue-500/50 outline-none transition-all placeholder:text-gray-700 shadow-2xl"
              @keydown.enter.exact.prevent="handleGenerate"
            ></textarea>
            <button 
              @click="handleGenerate"
              :disabled="!prompt.trim()"
              class="absolute bottom-4 right-4 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white px-8 py-3 rounded-2xl font-black flex items-center gap-2 shadow-xl transition-all hover:translate-y-[-2px] active:scale-95"
            >
              <Send class="w-5 h-5" />
              <span>开启任务</span>
            </button>
          </div>
        </div>

        <!-- Workbench Canvas -->
        <div v-if="isWorkbenchMode" class="h-full w-full bg-[#111] relative overflow-hidden">
          <transition name="fade" mode="out-in">
            <!-- Prototype View -->
            <div v-if="activeTab === 'design'" class="h-full bg-white relative">
               <div class="absolute inset-0 z-0 bg-slate-50 flex items-center justify-center opacity-10">
                  <div class="grid grid-cols-12 w-full h-full">
                    <div v-for="i in 144" :key="i" class="border-[0.5px] border-black/20"></div>
                  </div>
               </div>
               <iframe 
                 :srcdoc="result.manifest?.prototypeHtml"
                 class="w-full h-full border-none relative z-10"
               ></iframe>
            </div>

            <!-- Code Sandbox -->
            <div v-else-if="activeTab === 'preview'" class="h-full">
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
            <div v-else-if="activeTab === 'code'" class="h-full p-6 overflow-auto bg-[#0a0a0a]">
              <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <div v-for="(content, path) in result.files" :key="path" class="group bg-white/5 border border-white/5 rounded-xl overflow-hidden hover:border-white/20 transition-all">
                  <div class="px-4 py-2 bg-white/5 border-b border-white/5 flex justify-between items-center">
                    <span class="text-[10px] font-mono text-blue-400 opacity-60">{{ path }}</span>
                    <button class="opacity-0 group-hover:opacity-100 transition-opacity"><Code2 class="w-3.5 h-3.5 text-gray-500" /></button>
                  </div>
                  <pre class="p-4 text-[11px] font-mono text-gray-400 overflow-x-auto"><code>{{ content }}</code></pre>
                </div>
              </div>
            </div>

            <!-- Logic View -->
            <div v-else-if="activeTab === 'plan'" class="h-full p-12 overflow-auto bg-[#050505]">
               <div class="max-w-4xl mx-auto grid md:grid-cols-2 gap-8">
                  <section class="space-y-6">
                    <h3 class="text-xl font-black text-white flex items-center gap-3">
                      <div class="w-8 h-8 rounded-lg bg-blue-500/20 flex items-center justify-center text-blue-400"><Layers class="w-5 h-5"/></div>
                      功能矩阵
                    </h3>
                    <div v-for="f in result.manifest?.features" :key="f.name" class="p-5 rounded-2xl bg-white/5 border border-white/5 hover:bg-white/[0.07] transition-all">
                      <div class="flex justify-between items-start mb-2">
                        <h4 class="font-bold text-gray-100">{{ f.name }}</h4>
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
                    <div v-for="p in result.manifest?.pages" :key="p.route" class="flex gap-4 items-start p-4 rounded-xl hover:bg-white/5 transition-all">
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
            <h3 class="text-xl font-bold tracking-widest text-white uppercase">{{ generationPhase }} IN PROGRESS...</h3>
            <p class="text-gray-500 text-sm mt-1 italic">正在调度全球算力，请稍候...</p>
          </div>
        </div>
      </main>

      <!-- RIGHT: Copilot Sidebar -->
      <aside 
        v-if="isWorkbenchMode"
        class="border-l border-white/5 bg-black/40 backdrop-blur-3xl flex flex-col transition-all duration-500"
        :class="[isSidebarOpen ? 'w-[25%]' : 'w-0 opacity-0 invisible']"
      >
        <!-- Sidebar Header (Status) -->
        <div class="p-6 border-b border-white/5 bg-white/5">
          <div class="flex items-center justify-between mb-6">
            <span class="text-[10px] font-black text-gray-500 uppercase tracking-widest">任务指挥部</span>
            <button @click="isSidebarOpen = false" class="text-gray-600 hover:text-white"><PanelRight class="w-4 h-4" /></button>
          </div>
          
          <div class="space-y-4">
            <div v-for="(label, state) in { PLANNING: '需求分析', DESIGNING: '视觉对标', CODING: '代码合成', DONE: '资产交付' }" :key="state" class="flex items-center gap-3 group">
               <div 
                 :class="[
                   (result?.manifest?.status === state) ? 'bg-blue-500 scale-125 shadow-blue-500/50' : 
                   (isPastState(state) ? 'bg-green-500 opacity-40' : 'bg-gray-800')
                 ]"
                 class="w-2 h-2 rounded-full transition-all duration-700"
               ></div>
               <span :class="result?.manifest?.status === state ? 'text-white font-bold' : 'text-gray-600'" class="text-xs uppercase tracking-tighter">{{ label }}</span>
               <Loader2 v-if="result?.manifest?.status === state" class="w-3 h-3 text-blue-500 animate-spin ml-auto" />
            </div>
          </div>
        </div>

        <!-- Sidebar Actions Section -->
        <div v-if="generationPhase === 'awaiting_confirmation'" class="p-6 bg-blue-600/10 border-b border-blue-500/20">
           <h4 class="text-sm font-black text-white mb-2 italic">√ 原型已就绪</h4>
           <p class="text-[10px] text-gray-400 mb-4">当前展示的是视觉原型草案。如果您满意，请点击下方开启“全量编码”；如需调整，请在此对话。</p>
           <button @click="handleConfirm" class="w-full py-3 bg-white text-black font-black text-xs rounded-xl hover:scale-105 transition-all shadow-xl flex items-center justify-center gap-2">
              <Zap class="w-4 h-4 fill-current" />
              立即开始编码
           </button>
        </div>

        <!-- Chat / Prompt Area (Anchored to Bottom) -->
        <div class="flex-1 flex flex-col overflow-hidden">
          <div class="flex-1 p-6 overflow-y-auto space-y-4 no-scrollbar">
            <!-- Project Brief -->
            <div class="bg-white/5 rounded-2xl p-4 border border-white/5">
              <span class="text-[9px] font-black text-blue-500 uppercase">当前目标</span>
              <p class="text-xs text-gray-300 mt-1 leading-relaxed">{{ result.manifest?.userIntent }}</p>
            </div>
          </div>

          <!-- Bottom Prompt Panel -->
          <div class="p-6 bg-black/60 border-t border-white/5 space-y-3">
            <div class="relative">
              <textarea 
                v-model="prompt"
                placeholder="在此输入新的需求或修改..."
                class="w-full h-24 bg-white/10 border border-white/10 rounded-2xl p-4 text-xs focus:ring-2 focus:ring-blue-500/40 outline-none resize-none no-scrollbar transition-all"
                @keydown.enter.exact.prevent="handleGenerate"
              ></textarea>
              <button 
                @click="handleGenerate"
                :disabled="!prompt.trim() || loading"
                class="absolute bottom-3 right-3 p-2 bg-blue-600 rounded-lg disabled:opacity-50 text-white transition-all scale-90 hover:scale-100"
              >
                <Send class="w-4 h-4" />
              </button>
            </div>
            <div class="flex justify-between items-center px-1">
              <span class="text-[9px] text-gray-600 italic">由 LingNow AI 全程驱动</span>
              <button @click="resetProject" class="text-[9px] text-gray-400 hover:text-white flex items-center gap-1">
                 <Plus class="w-2.5 h-2.5"/> New Project
              </button>
            </div>
          </div>
        </div>
      </aside>

      <!-- Sidebar Toggle Badge -->
      <button 
        v-if="isWorkbenchMode && !isSidebarOpen"
        @click="isSidebarOpen = true"
        class="absolute right-6 bottom-6 w-12 h-12 bg-blue-600 rounded-full flex items-center justify-center shadow-2xl hover:scale-110 transition-all z-50 text-white"
        title="展开侧边栏"
      >
        <PanelRight class="w-6 h-6 rotate-180" />
      </button>
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
