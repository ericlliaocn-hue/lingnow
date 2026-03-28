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
    activeTab.value = 'plan'
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

// Inspector & Versioning State
// Inspector & Versioning State
const activeTab = ref('plan')
const selectedNode = ref(null)
const isDesignStarted = ref(false)
const snapshots = computed(() => result.value?.snapshots || [])
const currentVersion = computed(() => result.value?.version || '0.0.1')

const formatDate = (timestamp) => {
  if (!timestamp) return '未知'
  const date = new Date(timestamp)
  return date.toLocaleString(locale.value === 'ZH' ? 'zh-CN' : 'en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// Hybrid Map State
const renderFailed = ref(false)
const treeData = ref(null)
const lastParsedId = ref(null)

const parseMindMap = (text) => {
  if (!text) return null
  const lines = text.split('\n').filter(l => l.trim().length > 0)
  const root = {id: 'root-' + Math.random().toString(36).substr(2, 9), name: '', children: []}
  const stack = [{node: root, indent: -1}]

  lines.forEach(line => {
    const indent = line.search(/\S/)
    const name = line.trim()
    const node = {id: 'node-' + Math.random().toString(36).substr(2, 9), name, children: []}

    while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
      stack.pop()
    }

    if (stack.length > 0) {
      stack[stack.length - 1].node.children.push(node)
      stack.push({node, indent})
    }
  })

  return root.children[0] || root
}

const serializeTree = (node, indent = 0) => {
  if (!node) return ''
  let text = ' '.repeat(indent) + node.name + '\n'
  if (node.children) {
    node.children.forEach(child => {
      text += serializeTree(child, indent + 2)
    })
  }
  return text
}

const syncTreeToMindMap = () => {
  if (treeData.value && result.value) {
    result.value.mindMap = serializeTree(treeData.value)
  }
}

const addChild = (parent) => {
  if (!parent.children) parent.children = []
  parent.children.push({name: '新功能节点', children: []})
  syncTreeToMindMap()
}

const removeChild = (parent, index) => {
  parent.children.splice(index, 1)
  syncTreeToMindMap()
}

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
      securityLevel: 'loose',
      fontFamily: 'system-ui',
      mindmap: {useMaxWidth: true}
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
      selectedNode.value = {
        id: event.data.id,
        text: event.data.text,
        color: event.data.color,
        backgroundColor: event.data.backgroundColor,
        padding: event.data.padding,
        tagName: event.data.tagName
      }
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
  error.value = null
  generationPhase.value = 'PLANNING'

  const sessionId = result.value ? currentSessionId.value : `session-${Date.now()}`
  currentSessionId.value = sessionId

  try {
    const planRes = await axios.post('/api/generate/plan', {
      prompt: prompt.value,
      sessionId: sessionId,
      lang: locale.value
    })

    result.value = planRes.data
    activeTab.value = 'plan' // Switch to Plan tab to show the mindmap
    isDesignStarted.value = false
    
    if (route.params.id !== sessionId) {
      router.push(`/project/${sessionId}`)
    }
  } catch (err) {
    console.error('Generation failed', err)
    error.value = '规划失败'
  } finally {
    loading.value = false
    generationPhase.value = 'idle'
  }
}

const handleStartDesign = async () => {
  if (loading.value) return
  loading.value = true
  generationPhase.value = 'DESIGNING'
  try {
    const designRes = await axios.post('/api/generate/design', {
      sessionId: currentSessionId.value,
      lang: locale.value
    })
    result.value = designRes.data
    isDesignStarted.value = true
    activeTab.value = 'design'
    fetchHistory()
  } catch (err) {
    error.value = '视觉设计失败'
  } finally {
    loading.value = false
    generationPhase.value = 'idle'
  }
}

const updateNodeLive = () => {
  if (!selectedNode.value) return
  const iframe = document.querySelector('iframe')
  iframe?.contentWindow?.postMessage({
    type: 'lingnow-update-node',
    ...selectedNode.value
  }, '*')
}

const handleSaveSnapshot = async () => {
  try {
    const iframe = document.querySelector('iframe')
    const html = iframe.contentWindow.document.documentElement.outerHTML
    const res = await axios.post('/api/generate/snapshot', {
      sessionId: currentSessionId.value,
      html: html,
      summary: 'Manual refinement'
    })
    result.value = res.data
    alert('Version ' + result.value.version + ' saved successfully!')
  } catch (err) {
    alert('Save failed')
  }
}

const handleRollback = async (version) => {
  try {
    const res = await axios.post('/api/generate/rollback', {
      sessionId: currentSessionId.value,
      version: version
    })
    result.value = res.data
    activeTab.value = 'design'
  } catch (err) {
    alert('Rollback failed')
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
      
      function getStyle(el, prop) {
        return window.getComputedStyle(el).getPropertyValue(prop);
      }

      // Universal Interceptor: Prevent navigation away from prototype
      document.addEventListener('click', (e) => {
        const target = e.target;
        const link = target.closest('a');
        
        // Block external or relative links (non-hash)
        if (link) {
          const href = link.getAttribute('href');
          if (href && !href.startsWith('#') && !href.startsWith('javascript:')) {
            e.preventDefault();
            console.log('[LingNow Prototype] Navigation blocked:', href);
            return; // Block and stop propagation for non-internal links
          }
        }

        // Inspector logic (only if debug mode is active)
        if (!debugEnabled) return;
        
        const component = target.closest('[data-lingnow-id]')
        if (component) {
          e.preventDefault();
          window.parent.postMessage({ 
            type: 'lingnow-inspect', 
            id: component.getAttribute('data-lingnow-id'),
            text: component.innerText,
            color: getStyle(component, 'color'),
            backgroundColor: getStyle(component, 'background-color'),
            padding: getStyle(component, 'padding'),
            tagName: component.tagName.toLowerCase()
          }, '*')
          
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

      // Block form submissions from navigating
      document.addEventListener('submit', (e) => {
        e.preventDefault();
        console.log('[LingNow Prototype] Form submission blocked.');
      }, true);

      window.addEventListener('message', (e) => {
        if (e.data.type === 'lingnow-debug') {
          debugEnabled = e.data.enabled;
          if (!debugEnabled) {
             const badge = document.getElementById('lingnow-spec-badge');
             if (badge) badge.style.display = 'none';
             document.querySelectorAll('[data-lingnow-id]').forEach(el => el.style.outline = 'none');
          }
        } else if (e.data.type === 'lingnow-update-node') {
          const el = document.querySelector('[data-lingnow-id="' + e.data.id + '"]')
          if (el) {
            if (e.data.text !== undefined) el.innerText = e.data.text
            if (e.data.color) el.style.color = e.data.color
            if (e.data.backgroundColor) el.style.backgroundColor = e.data.backgroundColor
            if (e.data.padding) el.style.padding = e.data.padding
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
  activeTab.value = 'plan'
  if (route.path !== '/') router.push('/')
}

watch([activeTab, () => result.value?.id, () => result.value?.mindMap], async ([tab, id, map]) => {
  if (tab === 'plan' && map) {
    // Only re-parse if we don't have treeData OR if a fundamentally different project is loaded
    // BUT we also need to allow re-parse if map fundamentally changed from outside
    if (!treeData.value || lastParsedId.value !== id) {
      console.log('Project changed or fresh load. Parsing tree data.')
      treeData.value = parseMindMap(map)
      lastParsedId.value = id
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
              v-for="t in [{id: 'plan', icon: Layers, label: '功能架构'},{id: 'design', icon: Layout, label: '核心原型'},{id: 'code', icon: Terminal, label: i18n.tab_source},{id: 'preview', icon: Box, label: i18n.tab_sandbox}]"
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

        <div v-show="isWorkbenchMode"
             class="flex-1 relative overflow-hidden flex justify-center bg-[#0d0d0d] no-scrollbar">
          <transition name="fade">
            <div v-show="activeTab === 'design'"
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
          </transition>
          <transition name="fade">
            <div v-show="activeTab === 'plan'"
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
                    class="bg-white/5 border border-white/10 rounded-3xl p-12 overflow-x-auto min-h-[500px] flex items-center relative no-scrollbar">
                  <!-- Horizontal Blueprint Tree (Native Vue) -->
                  <div v-if="treeData" class="flex items-center py-8 animate-in slide-in-from-left duration-700">
                    <div class="tree-root flex items-center">
                      <!-- Root Node (Left) -->
                      <div
                          class="group relative px-8 py-4 bg-blue-600 text-white rounded-2xl font-black text-base shadow-2xl shadow-blue-500/20 border border-blue-400/50 shrink-0">
                        <input v-model="treeData.name" class="bg-transparent border-none outline-none text-center min-w-[120px]"
                               @blur="syncTreeToMindMap"/>
                        <button class="absolute -bottom-10 left-1/2 -translate-x-1/2 p-2 bg-blue-500/20 text-blue-500 rounded-full opacity-0 group-hover:opacity-100 transition-all hover:bg-blue-500 hover:text-white"
                                @click="addChild(treeData)">
                          <span class="text-xl leading-none">+</span>
                        </button>
                      </div>

                      <!-- Branches (Expanding to Right) -->
                      <div class="tree-branches flex flex-col gap-16 ml-24 relative">
                        <!-- Main Horizontal Connector from Root to Branch List -->
                        <div class="absolute -left-20 top-1/2 w-20 h-px bg-blue-500/30"></div>
                        <!-- Vertical Grouping Line -->
                        <div v-if="treeData.children.length > 1"
                             class="absolute -left-20 top-0 bottom-0 w-px bg-blue-500/20 my-4"></div>

                        <div v-for="(child, idx) in treeData.children" :key="child.id"
                             class="tree-node-group relative flex items-start gap-12">
                          <!-- Horizontal Connector from Vertical Line to Branch -->
                          <div class="absolute -left-20 top-6 w-20 h-px bg-blue-500/30"></div>

                          <!-- Module Node (Middle) -->
                          <div
                              class="group relative px-5 py-3 bg-white/5 border border-white/10 rounded-xl text-white font-bold text-sm hover:border-blue-500/50 transition-all shrink-0">
                            <input v-model="child.name" class="bg-transparent border-none outline-none text-left min-w-[100px]"
                                   @blur="syncTreeToMindMap"/>
                            <div
                                class="absolute -bottom-8 left-1/2 -translate-x-1/2 flex gap-1 opacity-0 group-hover:opacity-100 transition-all">
                              <button class="p-1.5 bg-blue-500/20 text-blue-500 rounded-md hover:bg-blue-500 hover:text-white"
                                      @click="addChild(child)">
                                <span class="text-xs">+</span></button>
                              <button class="p-1.5 bg-red-500/20 text-red-500 rounded-md hover:bg-red-500 hover:text-white"
                                      @click="removeChild(treeData, idx)">
                                <span class="text-xs">×</span></button>
                            </div>
                          </div>

                          <!-- Leaf Features (Right) -->
                          <div v-if="child.children && child.children.length > 0"
                               class="flex flex-col gap-4 py-1 relative">
                            <!-- Vertical Feature Connectors -->
                            <div v-if="child.children.length > 1"
                                 class="absolute -left-6 top-5 bottom-5 w-px bg-white/5"></div>
                            <div v-for="(sub, sIdx) in child.children" :key="sub.id"
                                 class="group relative px-4 py-2 bg-white/5 border border-white/5 rounded-lg text-gray-400 text-xs hover:border-blue-500/30 transition-all flex items-center">
                              <div class="absolute -left-6 top-1/2 -translate-y-1/2 w-6 h-px bg-white/5"></div>
                              <input v-model="sub.name" class="bg-transparent border-none outline-none text-left min-w-[80px]"
                                     @blur="syncTreeToMindMap"/>
                              <div
                                  class="absolute -right-16 top-1/2 -translate-y-1/2 flex gap-1 opacity-0 group-hover:opacity-100 transition-all">
                                <button class="p-1.5 bg-blue-500/20 text-blue-500 rounded-md hover:bg-blue-500 hover:text-white"
                                        @click="addChild(sub)">
                                  <span class="text-xs">+</span></button>
                                <button class="p-1 text-red-500 hover:text-red-400" @click="removeChild(child, sIdx)">
                                  ×
                                </button>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-6 pb-24">
                  <div v-for="f in result?.features" :key="f.name"
                       class="p-6 bg-white/5 border border-white/5 rounded-2xl hover:border-blue-500/30 transition-all">
                    <span class="text-[9px] font-black text-blue-500 uppercase tracking-widest">{{ f.priority }} PRIORITY</span>
                    <h4 class="text-white font-bold mt-1">{{ f.name }}</h4>
                    <p class="text-gray-500 text-xs mt-2 leading-relaxed">{{ f.description }}</p>
                  </div>
                </div>

                <!-- Serial Flow Confirmation -->
                <div v-if="!isDesignStarted && result?.status === 'PLANNING'"
                     class="fixed bottom-12 left-1/2 -translate-x-1/2 z-50 animate-bounce">
                  <button class="flex items-center gap-3 px-10 py-5 bg-blue-600 hover:bg-blue-500 text-white rounded-full font-black text-xl shadow-[0_20px_50px_rgba(37,99,235,0.4)] transition-all"
                          @click="handleStartDesign">
                    <Sparkles class="w-6 h-6"/>
                    确定脑图，开始生成视觉原型
                  </button>
                </div>
              </div>
            </div>
          </transition>

          <transition name="fade">
            <div v-show="activeTab === 'code'" class="h-full w-full overflow-auto p-12 bg-[#0d0d0d]">
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
          </transition>

          <transition name="fade">
            <div v-show="activeTab === 'preview'" class="h-full w-full">
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
        <div class="flex-1 p-6 overflow-auto space-y-8 no-scrollbar">
          <!-- Current Version Info -->
          <div class="flex items-center justify-between">
            <span class="text-[10px] font-black text-blue-500 uppercase tracking-widest">Version Alpha</span>
            <span class="px-2 py-0.5 bg-blue-500/20 text-blue-400 rounded text-[10px] font-bold">v{{
                currentVersion
              }}</span>
          </div>

          <div v-if="result" class="bg-white/5 rounded-2xl p-4 border border-white/5">
            <span class="text-[9px] font-black text-gray-500 uppercase tracking-widest">当前目标</span>
            <p class="text-xs text-gray-300 mt-1 leading-relaxed">{{ result.userIntent }}</p>
          </div>

          <!-- Inspector Toolbox -->
          <div v-if="isDebugMode && selectedNode"
               class="space-y-4 animate-in fade-in slide-in-from-right-4 duration-300">
            <div class="flex items-center justify-between border-b border-white/5 pb-2">
              <span
                  class="text-[10px] font-black text-purple-400 uppercase tracking-widest italic">Inspector Toolbox</span>
              <span class="text-[10px] text-gray-600 font-mono">&lt;{{ selectedNode.tagName }}&gt;</span>
            </div>

            <div class="space-y-4">
              <div class="space-y-1.5">
                <label class="text-[10px] font-bold text-gray-500 uppercase">InnerText</label>
                <textarea v-model="selectedNode.text" class="w-full bg-black border border-white/10 rounded-lg p-3 text-xs text-white focus:border-purple-500 transition-all no-scrollbar h-20"
                          @input="updateNodeLive"></textarea>
              </div>

              <div class="grid grid-cols-2 gap-3">
                <div class="space-y-1.5">
                  <label class="text-[10px] font-bold text-gray-500 uppercase">Text Color</label>
                  <input v-model="selectedNode.color" class="w-full bg-black border border-white/10 rounded-lg p-2 text-[10px] text-white font-mono" type="text"
                         @input="updateNodeLive"/>
                </div>
                <div class="space-y-1.5">
                  <label class="text-[10px] font-bold text-gray-500 uppercase">Background</label>
                  <input v-model="selectedNode.backgroundColor" class="w-full bg-black border border-white/10 rounded-lg p-2 text-[10px] text-white font-mono" type="text"
                         @input="updateNodeLive"/>
                </div>
              </div>

              <div class="space-y-1.5">
                <label class="text-[10px] font-bold text-gray-500 uppercase">Padding (CSS)</label>
                <input v-model="selectedNode.padding" class="w-full bg-black border border-white/10 rounded-lg p-2 text-[10px] text-white font-mono" type="text"
                       @input="updateNodeLive"/>
              </div>

              <button class="w-full py-3 bg-purple-600 hover:bg-purple-500 text-white font-black text-xs rounded-xl shadow-lg transition-all active:scale-95 flex items-center justify-center gap-2"
                      @click="handleSaveSnapshot">
                <Send class="w-3.5 h-3.5"/>
                保存修改并迭代版本
              </button>
            </div>
          </div>

          <!-- History / Snapshots -->
          <div v-if="snapshots.length > 0" class="space-y-4">
            <span class="text-[10px] font-black text-gray-500 uppercase tracking-widest">存档历史 (Rollback)</span>
            <div class="space-y-2">
              <div v-for="s in [...snapshots].reverse()" :key="s.version"
                   class="p-3 bg-white/5 border border-white/5 rounded-xl cursor-pointer hover:border-blue-500/40 transition-all flex items-center justify-between group"
                   @click="handleRollback(s.version)">
                <div class="flex flex-col">
                  <div class="flex items-center gap-2">
                    <span class="text-[10px] font-black text-white group-hover:text-blue-400">v{{ s.version }}</span>
                    <span class="text-[8px] text-gray-600 font-mono">{{ formatDate(s.timestamp) }}</span>
                  </div>
                  <span class="text-[9px] text-gray-500 line-clamp-1">{{ s.summary || 'Snapshot' }}</span>
                </div>
                <History class="w-3.5 h-3.5 text-gray-700 group-hover:text-blue-500"/>
              </div>
            </div>
          </div>

          <button v-if="result?.status === 'DONE'"
                  class="w-full py-4 bg-white text-black font-black rounded-2xl hover:scale-105 active:scale-95 transition-all shadow-xl"
                  @click="handleConfirm">
            立即编码交付
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
                 class="group p-5 bg-white/5 border border-white/5 rounded-2xl cursor-pointer hover:bg-white/10 hover:border-blue-500/30 transition-all relative overflow-hidden"
                 @click="loadProject(proj.id)">
              <div class="flex justify-between items-start mb-2">
                <p class="text-xs text-white font-bold line-clamp-1 leading-relaxed flex-1">{{ proj.userIntent }}</p>
                <span class="text-[8px] px-1.5 py-0.5 bg-blue-500/10 text-blue-500 rounded font-black">{{
                    proj.version
                  }}</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-[9px] text-gray-600 uppercase tracking-widest font-bold">{{ proj.status }}</span>
                <span class="text-[8px] text-gray-500">最后编辑: {{ formatDate(proj.lastModified) }}</span>
              </div>
            </div>
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

/* Tree Styling (Horizontal) */
.tree-node-group {
  min-height: 80px;
}

.tree-branches {
  min-width: 400px;
}
</style>
