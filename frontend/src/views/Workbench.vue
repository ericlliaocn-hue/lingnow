<script setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import axios from 'axios'
import {
  Box,
  Globe,
  History,
  Layers,
  Layout,
  Loader2,
  LogOut,
  Monitor,
  PanelRight,
  Send,
  Smartphone,
  Sparkles,
  Tablet,
  Terminal,
  X,
  Zap
} from 'lucide-vue-next'
import {Sandpack} from 'sandpack-vue3'

const route = useRoute()
const router = useRouter()

const prompt = ref('')
const loading = ref(false)
const result = ref(null)
const error = ref(null)
const activeTab = ref('design')

const locale = ref(localStorage.getItem('lingnow_lang') || 'ZH')
const history = ref([])
const isHistoryOpen = ref(false)

const i18n = computed(() => ({
  ZH: {
    welcome: '你想建造什么？',
    subtitle: '告诉数字军团你的创意，我们将实时为您构建全栈应用。',
    placeholder: '在这里描述您的增量需求或新愿景...',
    tab_canvas: '视觉样机',
    tab_logic: '排期逻辑',
    tab_source: '源码视图',
    tab_sandbox: '在线沙盒',
    history_title: '项目档案管理',
    history_empty: '暂无存档记录',
    new_project: '新建项目',
    PLANNING: '需求分析',
    DESIGNING: '视觉对标',
    CODING: '代码合成',
    DONE: '资产交付',
    history: '项目档案',
    switch_lang: 'ZH/EN',
    action: '生成原型',
    loading_subtitle: '正在调度全球算力，请稍候...'
  },
  EN: {
    welcome: 'What do you want to build?',
    subtitle: 'Build full-stack apps in real-time.',
    placeholder: 'Describe your vision here...',
    tab_canvas: 'Canvas',
    tab_logic: 'Logic',
    tab_source: 'Source',
    tab_sandbox: 'Sandbox',
    history_title: 'Project Archive',
    history_empty: 'No archives yet',
    new_project: 'New Project',
    PLANNING: 'Planning',
    DESIGNING: 'Designing',
    CODING: 'Coding',
    DONE: 'Done',
    history: 'Archives',
    switch_lang: 'EN/ZH',
    action: 'Generate',
    loading_subtitle: 'Orchestrating computing power...'
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
  if (!id) return
  if (result.value && result.value.id === id) return // Already loaded

  console.log('Loading project from API:', id)
  loading.value = true
  error.value = null
  try {
    const res = await axios.get(`/api/projects/${id}`)
    result.value = res.data
    currentSessionId.value = id
    isHistoryOpen.value = false
    activeTab.value = 'design'
    if (route.params.id !== id) {
      router.push(`/project/${id}`)
    }
  } catch (err) {
    console.error('Failed to load project', err)
    error.value = '加载项目失败'
    result.value = null
  } finally {
    loading.value = false
  }
}

const isWorkbenchMode = computed(() => !!result.value && !error.value)
const isSidebarOpen = ref(true)
const deviceType = ref('desktop')
const isMirrorMode = ref(false)
const mirrorDevices = ref(['desktop', 'mobile'])
const isDebugMode = ref(false)

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
const authData = ref({username: 'eric', password: 'anbs,23t'})

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
    const res = await axios.post('/api/auth/login', authData.value)
    user.value = res.data
    localStorage.setItem('user', JSON.stringify(user.value))
    window.location.reload()
  } catch (err) {
    error.value = '认证失败'
  } finally {
    loading.value = false
  }
}

const handleLogout = () => {
  user.value = null
  localStorage.removeItem('user')
  window.location.reload()
}

watch(isDebugMode, (newVal) => {
  const iframes = document.querySelectorAll('iframe')
  iframes.forEach(iframe => {
    iframe.contentWindow?.postMessage({type: 'lingnow-debug', enabled: newVal}, '*')
  })
})

const inspectedId = ref(null)
const runtimeLogs = ref([])

onMounted(async () => {
  console.log('Workbench mounted. Route ID:', route.params.id)

  if (window.mermaid) {
    window.mermaid.initialize({
      theme: 'dark',
      startOnLoad: false,
      securityLevel: 'loose'
    })
  }

  if (route.params.id) {
    loadProject(route.params.id)
  }
  fetchHistory() // Ensure history is loaded on mount

  if (!user.value) return
  window.addEventListener('message', (event) => {
    if (!event.data || !event.data.type) return
    if (event.data.type === 'lingnow-inspect') {
      inspectedId.value = event.data.id
      if (activeTab.value === 'plan') {
        const el = document.getElementById('feature-' + event.data.id)
        if (el) el.scrollIntoView({behavior: 'smooth', block: 'center'})
      }
    } else if (event.data.type === 'lingnow-console') {
      runtimeLogs.value.push({
        level: event.data.level,
        msg: event.data.msg,
        time: new Date().toLocaleTimeString()
      })
      if (runtimeLogs.value.length > 50) runtimeLogs.value.shift()
    }
  })
})

watch(() => route.params.id, (newId) => {
  console.log('Route ID changed to:', newId)
  if (newId) {
    if (!result.value || result.value.id !== newId) {
      loadProject(newId)
    }
  } else {
    resetProject()
  }
})

const currentSessionId = ref(`session-${Date.now()}`)
const generationPhase = ref('idle')

const handleGenerate = async () => {
  if (!prompt.value.trim() || loading.value) return
  loading.value = true
  error.value = null // Clear previous errors
  generationPhase.value = 'PLANNING'

  // Create fresh ID if we are at home or starting a new project
  const sessionId = result.value ? currentSessionId.value : `session-${Date.now()}`
  currentSessionId.value = sessionId

  try {
    const planRes = await axios.post('/api/generate/plan', {
      prompt: prompt.value,
      sessionId: sessionId,
      lang: locale.value
    })

    // Set result immediately
    result.value = planRes.data
    console.log('Plan created:', result.value.id)

    // Update URL if needed
    if (route.params.id !== sessionId) {
      router.push(`/project/${sessionId}`)
    }

    generationPhase.value = 'DESIGNING'
    const designRes = await axios.post('/api/generate/design', {
      sessionId: sessionId,
      lang: locale.value
    })

    result.value = designRes.data
    console.log('Design created:', result.value.id)

    activeTab.value = 'design'
    prompt.value = ''
    fetchHistory()
  } catch (err) {
    console.error('Generation failed', err)
    error.value = '生成失败'
  } finally {
    loading.value = false
    generationPhase.value = 'idle'
  }
}

const handleConfirm = async () => {
  loading.value = true
  generationPhase.value = 'CODING'
  try {
    const response = await axios.post('/api/generate/develop', {
      sessionId: currentSessionId.value
    })
    result.value = response.data
    activeTab.value = 'preview'
  } catch (err) {
    error.value = '编码失败'
  } finally {
    loading.value = false
  }
}

const injectInspectScript = (html, enabled) => {
  if (!html) return html
  const script = `
    <script>
      let debugEnabled = ${enabled};
      const originalLog = console.log;
      const originalError = console.error;
      window.console.log = (...args) => {
        window.parent.postMessage({ type: 'lingnow-console', level: 'info', msg: args.join(' ') }, '*');
        originalLog.apply(console, args);
      };
      window.console.error = (...args) => {
        window.parent.postMessage({ type: 'lingnow-console', level: 'error', msg: args.join(' ') }, '*');
        originalError.apply(console, args);
      };

      document.addEventListener('click', (e) => {
        if (!debugEnabled) return;
        const component = e.target.closest('[data-lingnow-id]')
        if (component) {
          window.parent.postMessage({ type: 'lingnow-inspect', id: component.getAttribute('data-lingnow-id') }, '*')
          let badge = document.getElementById('lingnow-spec-badge')
          if (!badge) {
            badge = document.createElement('div')
            badge.id = 'lingnow-spec-badge'
            badge.style.cssText = "position:absolute;background:rgba(37,99,235,0.9);color:white;padding:4px 8px;border-radius:6px;font-size:11px;font-weight:800;z-index:99999;pointer-events:none;box-shadow:0 10px 15px -3px rgba(0,0,0,0.5);"
            document.body.appendChild(badge)
          }
          const rect = component.getBoundingClientRect()
          badge.innerHTML = '<span style="opacity:0.6;margin-right:4px">W x H</span> ' + Math.round(rect.width) + ' x ' + Math.round(rect.height)
          badge.style.top = (rect.top + window.scrollY - 30) + 'px'
          badge.style.left = (rect.left + window.scrollX) + 'px'
          badge.style.display = 'block'
          
          document.querySelectorAll('[data-lingnow-id]').forEach(el => el.style.outline = 'none')
          component.style.outline = '2px solid #3b82f6'
        }
      }, true)

      window.addEventListener('message', (e) => {
        if (e.data.type === 'lingnow-debug') {
          debugEnabled = e.data.enabled;
          document.querySelectorAll('.lingnow-debug-label').forEach(l => l.remove());
          if (!debugEnabled) {
            const badge = document.getElementById('lingnow-spec-badge');
            if (badge) badge.style.display = 'none';
            document.querySelectorAll('[data-lingnow-id]').forEach(el => el.style.outline = 'none');
          } else {
            document.querySelectorAll('[data-lingnow-id]').forEach(el => {
                const label = document.createElement('div');
                label.className = 'lingnow-debug-label';
                label.innerText = el.getAttribute('data-lingnow-name') || el.tagName.toLowerCase();
                label.style.cssText = "position:absolute;background:#9333ea;color:white;padding:2px 4px;font-size:9px;z-index:9998;pointer-events:none;border-radius:2px;";
                const rect = el.getBoundingClientRect();
                label.style.top = (rect.top + window.scrollY) + 'px';
                label.style.left = (rect.left + window.scrollX) + 'px';
                document.body.appendChild(label);
            })
          }
        }
      })
    <\/script>
  `
  return html.replace('</body>', script + '</body>')
}

const resetProject = () => {
  prompt.value = ''
  result.value = null
  error.value = null
  generationPhase.value = 'idle'
  activeTab.value = 'design'
  if (route.path !== '/') router.push('/')
}

watch([activeTab, () => result.value?.mindMap], async ([tab, map]) => {
  if (tab === 'plan' && map && window.mermaid) {
    await nextTick()
    try {
      const container = document.getElementById('mermaid-container')
      if (container) {
        container.removeAttribute('data-processed')
        container.innerHTML = map
        await window.mermaid.run({
          nodes: [container]
        })
      }
    } catch (err) {
      console.error('Mermaid render failed', err)
    }
  }
}, {immediate: true})
</script>

<template>
  <div class="h-full flex flex-col bg-black text-gray-300 select-none overflow-hidden">
    <nav
        class="h-16 flex items-center justify-between px-8 border-b border-white/5 bg-black/40 backdrop-blur-3xl z-40 shrink-0">
      <div class="flex items-center gap-6">
        <div class="flex items-center gap-3 cursor-pointer group" @click="resetProject">
          <div
              class="w-10 h-10 bg-gradient-to-br from-blue-600 to-indigo-700 rounded-xl flex items-center justify-center shadow-lg group-hover:shadow-blue-500/20 transition-all">
            <Zap class="w-6 h-6 text-white fill-current"/>
          </div>
          <h1 class="text-xl font-black text-white tracking-tighter uppercase italic">LingNow</h1>
        </div>

        <div v-if="isWorkbenchMode" class="flex bg-white/5 p-1 rounded-xl border border-white/5 ml-4">
          <button
              v-for="t in [{id: 'design', icon: Layout, label: i18n.tab_canvas},{id: 'plan', icon: Layers, label: i18n.tab_logic},{id: 'code', icon: Terminal, label: i18n.tab_source},{id: 'preview', icon: Box, label: i18n.tab_sandbox}]"
              :key="t.id"
              :class="[activeTab === t.id ? 'bg-white/10 text-white shadow-lg' : 'text-gray-500 hover:text-gray-300']"
              class="flex items-center gap-2 px-4 py-1.5 rounded-lg text-xs font-bold transition-all"
              @click="activeTab = t.id"
          >
            <component :is="t.icon" class="w-3.5 h-3.5"/>
            {{ t.label }}
          </button>
        </div>
      </div>

      <div class="flex items-center gap-4">
        <button
            class="flex items-center gap-2 px-3 py-2 rounded-xl bg-white/5 border border-white/5 hover:border-white/20 transition-all group"
            @click="isHistoryOpen = !isHistoryOpen; if(isHistoryOpen) fetchHistory()">
          <History class="w-4 h-4 group-hover:text-blue-400"/>
          <span class="text-[10px] font-black uppercase tracking-widest">{{ i18n.history }}</span>
        </button>
        <button
            class="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-white/5 border border-white/5 hover:border-white/20 transition-all"
            @click="toggleLang">
          <Globe class="w-3.5 h-3.5 text-gray-500"/>
          <span class="text-[10px] font-black">{{ i18n.switch_lang }}</span>
        </button>
        <div v-if="user" class="flex items-center gap-3 px-4 py-1.5 rounded-xl bg-white/5 border border-white/5">
          <span class="text-xs font-bold text-gray-200">{{ user.username }}</span>
          <button class="text-gray-600 hover:text-white transition-colors ml-2" @click="handleLogout">
            <LogOut class="w-4 h-4"/>
          </button>
        </div>
      </div>
    </nav>

    <div class="flex-1 flex overflow-hidden relative">
      <main
          :class="[isWorkbenchMode ? (isSidebarOpen ? 'w-[75%]' : 'w-full') : 'w-full flex items-center justify-center']"
          class="relative flex-1 flex flex-col bg-[#111] overflow-hidden">
        <div v-if="!isWorkbenchMode && !loading" class="max-w-2xl w-full px-6 text-center">
          <h2 class="text-5xl font-black mb-4 tracking-tighter text-white uppercase italic">{{ i18n.welcome }}</h2>
          <p class="text-gray-500 text-lg mb-12">{{ i18n.subtitle }}</p>
          <div class="relative group max-w-xl mx-auto">
            <textarea v-model="prompt" :placeholder="i18n.placeholder"
                      class="w-full h-40 bg-white/5 border border-white/10 rounded-3xl p-6 text-lg text-white outline-none focus:ring-4 focus:ring-blue-500/20 transition-all"></textarea>
            <button :disabled="!prompt.trim() || loading"
                    class="absolute bottom-4 right-4 bg-blue-600 text-white px-8 py-3 rounded-2xl font-black flex items-center gap-2"
                    @click="handleGenerate">
              <Send class="w-5 h-5"/>
              <span>{{ i18n.action }}</span>
            </button>
          </div>
        </div>

        <div v-if="isWorkbenchMode"
             class="h-12 bg-black/40 border-b border-white/5 flex items-center justify-center gap-4 shrink-0 px-4">
          <div class="flex items-center bg-white/5 rounded-lg p-1 border border-white/5">
            <button
                v-for="d in [{id:'desktop',icon:Monitor,label:'WEB视窗'},{id:'tablet',icon:Tablet,label:'平板视窗'},{id:'mobile',icon:Smartphone,label:'手机视窗'}]"
                :key="d.id"
                :class="[deviceType === d.id ? 'bg-blue-600 text-white shadow-lg' : 'text-gray-500 hover:text-gray-300']"
                class="p-1.5 rounded-md transition-all flex items-center gap-2"
                @click="deviceType = d.id">
              <component :is="d.icon" class="w-4 h-4"/>
              <span class="text-[9px] font-bold uppercase">{{ d.label }}</span>
            </button>
          </div>
          <button
              :class="[isDebugMode ? 'bg-purple-600 text-white shadow-lg border-purple-500' : 'text-gray-500 border-white/10 hover:border-white/20']"
              class="px-3 py-1.5 rounded-lg border text-[10px] font-black uppercase tracking-tighter transition-all flex items-center gap-2"
              @click="isDebugMode = !isDebugMode">
            <Sparkles class="w-3.5 h-3.5"/>
            {{ isDebugMode ? '关闭透视' : '开启透视' }}
          </button>
        </div>

        <div v-if="isWorkbenchMode"
             class="flex-1 relative overflow-hidden flex justify-center bg-[#0d0d0d] no-scrollbar">
          <transition name="fade">
            <div v-if="activeTab === 'design'" :key="'design'"
                 class="h-full w-full transition-all duration-500 ease-in-out relative origin-top flex flex-col items-center overflow-auto no-scrollbar bg-[#0d0d0d] p-0">
              <div :class="[deviceType === 'desktop' ? 'p-0' : 'p-12']"
                   class="flex-1 w-full flex justify-center items-center">
                <div
                    :class="[deviceType === 'desktop' ? 'w-full h-full border-none shadow-none rounded-none' : (deviceType === 'tablet' ? 'w-[768px] h-[95%] rounded-[3rem] border-[12px] border-[#1a1a1a] overflow-hidden' : 'w-[375px] h-[90%] rounded-[3rem] border-[12px] border-[#1a1a1a] overflow-hidden')]"
                    class="transition-all duration-700 relative origin-top shadow-[0_50px_120px_rgba(0,0,0,0.6)] flex justify-center shrink-0">
                  <iframe :srcdoc="injectInspectScript(result?.prototypeHtml, isDebugMode)"
                          class="w-full h-full border-none relative z-10 bg-white" frameborder="0"></iframe>
                </div>
              </div>
            </div>
            <div v-else-if="activeTab === 'plan'" :key="'plan'"
                 class="h-full w-full overflow-auto p-12 bg-[#0d0d0d] flex flex-col items-center">
              <div class="max-w-5xl w-full space-y-8">
                <div class="flex items-center justify-between">
                  <div>
                    <h2 class="text-3xl font-black text-white italic tracking-tighter uppercase">Architectural
                      Blueprint</h2>
                    <p class="text-gray-500 text-sm mt-1">AI 规划的功能逻辑脑图与交互架构</p>
                  </div>
                </div>
                <div
                    class="bg-white/5 border border-white/10 rounded-3xl p-8 overflow-x-auto min-h-[400px] flex justify-center">
                  <div id="mermaid-container" class="mermaid w-full flex justify-center">
                    {{ result?.mindMap || '等待思维导图生成...' }}
                  </div>
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div v-for="f in result?.features" :key="f.name"
                       class="p-6 bg-white/5 border border-white/5 rounded-2xl hover:border-blue-500/30 transition-all">
                    <span class="text-[9px] font-black text-blue-500 uppercase tracking-widest">{{ f.priority }} PRIORITY</span>
                    <h4 class="text-white font-bold mt-1">{{ f.name }}</h4>
                    <p class="text-gray-500 text-xs mt-2 leading-relaxed">{{ f.description }}</p>
                  </div>
                </div>
              </div>
            </div>

            <div v-else-if="activeTab === 'code'" :key="'code'" class="h-full w-full overflow-auto p-12 bg-[#0d0d0d]">
              <div class="max-w-4xl mx-auto space-y-6">
                <h2 class="text-xl font-black text-white italic tracking-tighter uppercase">Source Inventory</h2>
                <div class="grid grid-cols-1 gap-4">
                  <div v-for="(content, path) in result?.files" :key="path"
                       class="p-4 bg-white/5 border border-white/5 rounded-xl flex items-center justify-between group">
                    <div class="flex items-center gap-3">
                      <Terminal class="w-4 h-4 text-gray-500"/>
                      <span class="text-sm font-mono text-gray-300">{{ path }}</span>
                    </div>
                    <span class="text-[10px] text-gray-600 uppercase font-black">{{ content.length }} bytes</span>
                  </div>
                </div>
              </div>
            </div>

            <div v-else-if="activeTab === 'preview'" :key="'preview'" class="h-full w-full">
              <Sandpack :files="sandpackFiles"
                        :options="{ editorHeight: '100%', externalResources: ['https://cdn.tailwindcss.com'] }"
                        class="h-full" template="vue3"
                        theme="dark"/>
            </div>
          </transition>
        </div>

        <div v-if="loading"
             class="absolute inset-0 bg-black/60 backdrop-blur-md z-max flex flex-col items-center justify-center gap-6">
          <Loader2 class="w-16 h-16 text-blue-500 animate-spin"/>
          <h3 class="text-xl font-bold tracking-widest text-white uppercase">{{ generationPhase }} IN PROGRESS...</h3>
        </div>
      </main>

      <aside v-if="isWorkbenchMode && isSidebarOpen"
             class="w-[25%] border-l border-white/5 bg-black/40 backdrop-blur-3xl flex flex-col">
        <div class="p-6 border-b border-white/5 bg-white/5 flex justify-between items-center"><span
            class="text-[10px] font-black text-gray-500 uppercase tracking-widest">任务指挥部</span>
          <button @click="isSidebarOpen = false">
            <PanelRight class="w-4 h-4"/>
          </button>
        </div>
        <div class="flex-1 p-6 overflow-auto space-y-6">
          <div v-if="result" class="bg-white/5 rounded-2xl p-4 border border-white/5"><span
              class="text-[9px] font-black text-blue-500 uppercase tracking-widest">当前目标</span>
            <p class="text-xs text-gray-300 mt-1 leading-relaxed">{{ result.userIntent }}</p></div>
          <button v-if="result?.status === 'DONE'"
                  class="w-full py-4 bg-white text-black font-black rounded-2xl hover:scale-105 active:scale-95 transition-all shadow-xl"
                  @click="handleConfirm">
            立即编码
          </button>
        </div>
      </aside>

      <transition name="drawer">
        <div v-if="isHistoryOpen"
             class="absolute inset-y-0 right-0 z-[60] w-96 bg-[#0a0a0a]/90 backdrop-blur-3xl border-l border-white/10 shadow-[-20px_0_60px_rgba(0,0,0,0.8)] flex flex-col overflow-hidden">
          <div class="p-8 border-b border-white/5 flex items-center justify-between"><h2
              class="text-xl font-black text-white italic tracking-tighter">项目档案库</h2>
            <button @click="isHistoryOpen = false">
              <X class="w-5 h-5"/>
            </button>
          </div>
          <div class="flex-1 overflow-y-auto p-4 space-y-3 no-scrollbar">
            <div v-for="proj in history" :key="proj.id"
                 class="group p-4 bg-white/5 border border-white/5 rounded-2xl cursor-pointer hover:bg-white/10 hover:border-blue-500/30 transition-all relative overflow-hidden"
                 @click="loadProject(proj.id)">
              <p class="text-xs text-white font-bold line-clamp-2 leading-relaxed">{{ proj.userIntent }}</p></div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<style>
.z-max {
  z-index: 9999;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

.drawer-enter-active, .drawer-leave-active {
  transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

.drawer-enter-from, .drawer-leave-to {
  transform: translateX(105%);
  opacity: 0;
}

html, body {
  background: black;
  margin: 0;
  overflow: hidden;
  height: 100vh;
  font-family: sans-serif;
}

#app {
  height: 100vh;
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}

.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
