package cc.lingnow.service;

import cc.lingnow.llm.LlmClient;
import cc.lingnow.model.ProjectManifest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * UI Designer Agent - "Logic Layer Injection" Architecture
 *
 * Java controls ALL business logic (injected as Alpine.js Store).
 * LLM generates only visual HTML, using $store.app.* API.
 * Industry detection ensures views and store extensions are domain-appropriate.
 *
 * Supported industries: COMMUNITY, ECOMMERCE, SAAS, SOCIAL, BOOKING, EDUCATION, FINANCE, DEFAULT
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UiDesignerAgent {

    private final LlmClient llmClient;

    // ==========================================
    // INDUSTRY DETECTION
    // ==========================================

    private Industry detectIndustry(String intent) {
        if (intent == null) return Industry.DEFAULT;
        String s = intent.toLowerCase();
        if (s.matches(".*?(shop|store|mall|market|product|cart|buy|sell|price|sku|inventory|ecommerce|e-commerce|\\u5546\\u57ce|\\u5546\\u5e97|\\u5546\\u54c1|\\u8d2d\\u7269|\\u4ef7\\u683c|\\u5e93\\u5b58).*"))
            return Industry.ECOMMERCE;
        if (s.matches(".*?(dashboard|analytics|admin|crm|erp|saas|metric|report|monitor|management|console|panel|\\u4eea\\u8868\\u76d8|\\u7ba1\\u7406|\\u540e\\u53f0|\\u62a5\\u8868|\\u76d1\\u63a7).*"))
            return Industry.SAAS;
        if (s.matches(".*?(social|friend|follow|dating|match|network|\\u670b\\u53cb|\\u5173\\u6ce8|\\u793e\\u4ea4|\\u4ea4\\u53cb|\\u964c\\u751f\\u4eba).*"))
            return Industry.SOCIAL;
        if (s.matches(".*?(hospital|clinic|doctor|patient|appointment|medical|health|booking|\\u9884\\u7ea6|\\u533b\\u9662|\\u533b\\u751f|\\u8bca\\u6240|\\u6302\\u53f7|\\u5065\\u5eb7).*"))
            return Industry.BOOKING;
        if (s.matches(".*?(course|learn|school|exam|quiz|student|teacher|edu|\\u8bfe\\u7a0b|\\u5b66\\u4e60|\\u6559\\u80b2|\\u8003\\u8bd5|\\u5b66\\u6821|\\u57f9\\u8bad).*"))
            return Industry.EDUCATION;
        if (s.matches(".*?(bank|finance|invest|wallet|payment|fund|stock|crypto|\\u7406\\u8d22|\\u94f6\\u884c|\\u6295\\u8d44|\\u652f\\u4ed8|\\u57fa\\u91d1|\\u80a1\\u7968).*"))
            return Industry.FINANCE;
        if (s.matches(".*?(video|movie|film|stream|vlog|youtube|player|short|netflix|cinema|\\u89c6\\u9891|\\u7535\\u5f71|\\u64ad\\u653e|\\u76f4\\u64ad|\\u77ed\\u89c6\\u9891).*"))
            return Industry.VIDEO;
        if (s.matches(".*?(blog|forum|wiki|community|post|article|comment|discuss|linuxdo|\\u535a\\u5ba2|\\u793e\\u533a|\\u8bba\\u575b|\\u5e16\\u5b50|\\u6587\\u7ae0|\\u8ba8\\u8bba).*"))
            return Industry.COMMUNITY;
        return Industry.DEFAULT;
    }

    public void design(ProjectManifest manifest) {
        log.info("[UiDesigner] Starting pipeline for: {}", manifest.getUserIntent());
        String lang = manifest.getMetaData() != null ? manifest.getMetaData().getOrDefault("lang", "ZH") : "ZH";
        Industry industry = detectIndustry(manifest.getUserIntent());
        String rawMock = manifest.getMockData();
        // If upstream didn't generate mock data, create industry-appropriate fallback
        String mockDataJson = (rawMock == null || rawMock.trim().isEmpty() || rawMock.trim().equals("[]") || rawMock.trim().equals("null"))
                ? generateFallbackMockData(industry, manifest.getUserIntent())
                : rawMock;
        log.info("[UiDesigner] Industry: {}. Mock data: {} chars.", industry, mockDataJson.length());

        try {
            List<String> featureNodes = parseFeatureNodes(manifest.getMindMap());

            String storeScript = hydrateStore(mockDataJson, industry);
            String shellHtml = generateShell(manifest, featureNodes, lang, industry);

            StringBuilder slots = new StringBuilder();
            slots.append(buildIndexView(lang, industry)).append("\n");
            slots.append(buildDetailView(lang, industry)).append("\n");
            slots.append(buildProfileView(lang)).append("\n");
            for (int i = 0; i < Math.min(featureNodes.size(), 5); i++) {
                String node = featureNodes.get(i);
                slots.append(generateFeaturePage(manifest, encodeRouteId(node), node, lang)).append("\n");
            }

            String assembled = assemble(shellHtml, slots.toString());
            String finalHtml = injectStore(assembled, storeScript);
            manifest.setPrototypeHtml(finalHtml);
            log.info("[UiDesigner] Done. {} chars.", finalHtml.length());

        } catch (Exception e) {
            log.error("[UiDesigner] Failed", e);
            throw new RuntimeException("UI Design failed: " + e.getMessage());
        }
    }

    // ==========================================
    // PUBLIC API
    // ==========================================

    public void redesign(ProjectManifest manifest, String instructions) {
        String systemPrompt = "You are a UIUX Refinement Agent.\n"
                + "Instructions: " + instructions + "\n"
                + "RULES: Do NOT remove <script> tags, do NOT rewrite $store.app.* bindings. Only change CSS and layout.\n"
                + "Output raw full HTML only.";
        try {
            manifest.setPrototypeHtml(parseHtmlSnippet(llmClient.chat(systemPrompt, manifest.getPrototypeHtml())));
        } catch (Exception e) {
            throw new RuntimeException("Redesign failed: " + e.getMessage());
        }
    }

    private String hydrateStore(String mockDataJson, Industry industry) throws IOException {
        ClassPathResource resource = new ClassPathResource("static/prototype/store.template.js");
        String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String safeJson = mockDataJson.replace("</script>", "<\\/script>");
        String hydrated = template.replace("MOCK_DATA_JSON", safeJson);
        return "<script>\n" + hydrated + "\n" + buildStoreExtension(industry) + "\n</script>";
    }

    // ==========================================
    // STEP 1: HYDRATE ALPINE.JS STORE
    // ==========================================

    private String buildStoreExtension(Industry industry) {
        switch (industry) {
            case ECOMMERCE:
                return "document.addEventListener('alpine:init', function() {"
                        + "  var s = Alpine.store('app');"
                        + "  s.cart = []; s.cartCount = 0; s.showCart = false;"
                        + "  s.addToCart = function(item) {"
                        + "    var f = this.cart.find(function(c){return c.id===item.id;});"
                        + "    if(f){f.qty++;}else{this.cart.push(Object.assign({qty:1},item));}"
                        + "    this.cartCount = this.cart.reduce(function(s,c){return s+c.qty;},0);"
                        + "    this.toast((item.title||item.name||'\u5546\u54c1')+' \u5df2\u52a0\u5165\u8d2d\u7269\u8f66');"
                        + "  };"
                        + "  s.removeFromCart = function(item) {"
                        + "    this.cart = this.cart.filter(function(c){return c.id!==item.id;});"
                        + "    this.cartCount = this.cart.reduce(function(s,c){return s+c.qty;},0);"
                        + "  };"
                        + "});";
            case BOOKING:
                return "document.addEventListener('alpine:init', function() {"
                        + "  var s = Alpine.store('app');"
                        + "  s.appointments = []; s.bookingStep = 0; s.selectedDoctor = null; s.selectedDate = '';"
                        + "  s.bookAppointment = function(doctor, date) {"
                        + "    if(!this.isLoggedIn){this.showAuthModal=true;return;}"
                        + "    this.appointments.push({id:Date.now(),doctor:doctor,date:date,status:'\u5df2\u9884\u7ea6'});"
                        + "    this.toast('\u9884\u7ea6\u6210\u529f!');"
                        + "  };"
                        + "});";
            case EDUCATION:
                return "document.addEventListener('alpine:init', function() {"
                        + "  var s = Alpine.store('app');"
                        + "  s.enrolledCourses = [];"
                        + "  s.enroll = function(course) {"
                        + "    if(!this.isLoggedIn){this.showAuthModal=true;return;}"
                        + "    if(this.enrolledCourses.find(function(c){return c.id===course.id;})){this.toast('\u5df2\u62a5\u540d');return;}"
                        + "    this.enrolledCourses.push(course);"
                        + "    this.toast('\u62a5\u540d\u6210\u529f! '+(course.title||course.name));"
                        + "  };"
                        + "  s.isEnrolled = function(course){"
                        + "    return !!this.enrolledCourses.find(function(c){return c.id===course.id;});"
                        + "  };"
                        + "});";
            case FINANCE:
                return "document.addEventListener('alpine:init', function() {"
                        + "  var s = Alpine.store('app');"
                        + "  s.portfolio = []; s.balance = 50000;"
                        + "  s.buyAsset = function(asset, amount) {"
                        + "    if(!this.isLoggedIn){this.showAuthModal=true;return;}"
                        + "    if(this.balance<amount){this.toast('\u4f59\u989d\u4e0d\u8db3');return;}"
                        + "    this.balance-=amount;"
                        + "    this.portfolio.push({id:Date.now(),asset:asset,amount:amount,time:'\u521a\u521a'});"
                        + "    this.toast('\u4ea4\u6613\u6210\u529f');"
                        + "  };"
                        + "});";
            default:
                return "/* base store only */";
        }
    }

    private String generateShell(ProjectManifest manifest, List<String> featureNodes, String lang, Industry industry) {
        boolean zh = lang.equals("ZH");
        String navItems = String.join(", ", featureNodes.subList(0, Math.min(featureNodes.size(), 6)));

        String palette = getThemePalette(manifest.getUserIntent());
        String bodyClasses = getThemeColors(manifest.getUserIntent());

        String industryDesc;
        String createHint;
        switch (industry) {
            case ECOMMERCE:
                industryDesc = "E-Commerce/Shopping platform";
                createHint = "List products button. Cart icon in nav with badge ($store.app.cartCount).";
                break;
            case SAAS:
                industryDesc = "SaaS/Management Dashboard";
                createHint = "Create Record button and date range filter in nav.";
                break;
            case BOOKING:
                industryDesc = "Healthcare/Booking platform";
                createHint = "Book appointment button. My Appointments link in nav.";
                break;
            case EDUCATION:
                industryDesc = "Education/E-Learning platform";
                createHint = "My Courses link in nav.";
                break;
            case FINANCE:
                industryDesc = "Finance/Investment platform";
                createHint = "Account balance display in nav ($store.app.balance).";
                break;
            default:
                industryDesc = "Community/Blog platform";
                createHint = "Post button in nav.";
        }

        String systemPrompt = "You are a World-Class Frontend Engineer. Build a COMPLETE HTML5 SPA shell for: " + industryDesc + "\n\n"
                + "=== THEME PALETTE (MANDATORY) ===\n"
                + palette + "\n\n"
                + "=== ALPINE.JS STORE API (pre-built, DO NOT redefine) ===\n"
                + "State: $store.app.activeTab, isLoggedIn, currentUser, searchQuery, showAuthModal, showPostModal, showNotif, unreadCount, toastVisible, toastMessage\n"
                + "Methods: login(u,p), logout(), nav('#HASH'), toast('msg'), openPost(item), likePost(item), submitPost(), markAllRead()\n\n"
                + "=== MANDATORY CDN in <head> ===\n"
                + "<script src=\"https://cdn.tailwindcss.com\"></script>\n"
                + "<script defer src=\"https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js\"></script>\n"
                + "<link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css\" rel=\"stylesheet\">\n\n"
                + "=== SHELL STRUCTURE ===\n"
                + "1. <body> root: <div x-data class=\"min-h-screen " + bodyClasses + " font-sans transition-colors duration-500\">\n"
                + "2. TOP NAV: Logo | Search(x-model=\"$store.app.searchQuery\") | Bell(@click=\"$store.app.showNotif=!$store.app.showNotif\" + badge) | " + createHint + " | Login(@click=\"$store.app.showAuthModal=true\" when !isLoggedIn) | Avatar(when isLoggedIn)\n"
                + "3. SIDEBAR: Nav links with @click=\"$store.app.nav('#HASH')\", active highlight logic.\n"
                + "4. AUTH MODAL: x-show=\"$store.app.showAuthModal\". x-ref=\"username\" + x-ref=\"password\". Confirm: @click=\"$store.app.login($refs.username.value, $refs.password.value)\"\n"
                + "5. CREATE MODAL: x-show=\"$store.app.showPostModal\". Submit: @click=\"$store.app.submitPost()\"\n"
                + "6. NOTIFICATION PANEL: x-show=\"$store.app.showNotif\".\n"
                + "7. TOAST: Fixed bottom-right.\n"
                + "8. CONTENT AREA: Must contain exactly: {{CONTENT_SLOTS}}\n"
                + "9. STYLE: MATCH THE THEME PALETTE (e.g., if Black/Orange, use #000 for bg and #ff9900 for primary elements). Use glassmorphism.\n"
                + "10. LANGUAGE: " + (zh ? "Chinese" : "English") + "\n"
                + "OUTPUT: raw HTML in ```html";

        try {
            return parseHtmlSnippet(llmClient.chat(systemPrompt,
                    "App: " + manifest.getUserIntent() + "\nNav items: " + navItems + ", Profile, Notifications\nIndustry: " + industry));
        } catch (IOException e) {
            throw new RuntimeException("Shell generation failed", e);
        }
    }

    // ==========================================
    // STEP 2: LLM-GENERATED SHELL (INDUSTRY-AWARE)
    // ==========================================

    private String buildIndexView(String lang, Industry industry) {
        switch (industry) {
            case ECOMMERCE:
                return buildEcommerceView(lang);
            case SAAS:
                return buildSaasView(lang);
            case BOOKING:
                return buildBookingView(lang);
            case EDUCATION:
                return buildEducationView(lang);
            case VIDEO:
                return buildVideoView(lang);
            default:
                return buildCommunityView(lang);
        }
    }

    // ==========================================
    // STEP 3: INDUSTRY-AWARE VIEWS (JAVA-CONTROLLED)
    // ==========================================

    // ----- COMMUNITY / BLOG / DEFAULT -----
    private String buildCommunityView(String lang) {
        boolean zh = lang.equals("ZH");
        String newBtn = zh ? "&#x53D1;&#x8D34;" : "New Post";
        String title = zh ? "&#x6700;&#x65B0;&#x52A8;&#x6001;" : "Latest Feed";
        String noResult = zh ? "&#x6CA1;&#x6709;&#x76F8;&#x5173;&#x5185;&#x5BB9;" : "No results found";
        String anon = zh ? "&#x533F;&#x540D;" : "Anonymous";

        return "<div x-show=\"$store.app.activeTab === '#INDEX'\" class=\"p-6\">"
                + toast()
                + "<div class=\"flex items-center justify-between mb-10\">"
                + "  <div class=\"flex flex-col\">"
                + "    <h2 class=\"text-3xl font-black italic tracking-tighter uppercase\">" + title + "</h2>"
                + "    <div class=\"h-1.5 w-12 bg-orange-500 mt-1 rounded-full animate-pulse\"></div>"
                + "  </div>"
                + "  <button @click=\"$store.app.isLoggedIn ? ($store.app.showPostModal=true) : ($store.app.showAuthModal=true)\""
                + "   class=\"bg-orange-600 hover:bg-orange-500 active:scale-95 transition-all text-white px-8 py-3 rounded-2xl text-sm font-black shadow-[0_8px_20px_rgba(255,153,0,0.3)]\">"
                + "   <i class=\"fa fa-plus-circle mr-2\"></i>" + newBtn + "</button>"
                + "</div>"
                + "<div class=\"grid grid-cols-1 md:grid-cols-2 xl:grid-cols-2 gap-10\">"
                + "  <template x-for=\"post in $store.app.filteredPosts\" :key=\"post.id\">"
                + "    <div @click=\"$store.app.openPost(post)\" class=\"bg-slate-800/20 backdrop-blur-xl border border-slate-700/50 rounded-[2.5rem] overflow-hidden hover:border-orange-500/50 transition-all group cursor-pointer flex flex-col shadow-2xl\">"
                + "      <div class=\"h-56 w-full bg-slate-900 relative overflow-hidden\">"
                + "        <img :src=\"post.cover || 'https://images.unsplash.com/photo-1614850523296-d8c1af93d400?q=80&w=800'\" class=\"w-full h-full object-cover group-hover:scale-110 transition-transform duration-700 opacity-60 group-hover:opacity-100\" />"
                + "        <div class=\"absolute top-5 left-5 flex gap-2\">"
                + "          <template x-for=\"tag in (post.tags||[])\">"
                + "            <span class=\"bg-black/80 backdrop-blur-lg text-orange-400 text-[11px] px-3 py-1 rounded-xl font-black uppercase tracking-widest border border-orange-500/20\" x-text=\"tag\"></span>"
                + "          </template>"
                + "        </div>"
                + "        <div class=\"absolute inset-0 bg-gradient-to-t from-slate-900 via-transparent to-transparent opacity-80\"></div>"
                + "      </div>"
                + "      <div class=\"p-8 -mt-6 relative\">"
                + "        <h3 class=\"text-white font-black text-2xl mb-4 leading-tight group-hover:text-orange-400 transition-colors\" x-text=\"post.title||post.name||'Untitled'\"></h3>"
                + "        <p class=\"text-slate-400 text-sm mb-8 line-clamp-3 leading-loose shadow-sm\" x-text=\"post.description||post.content||''\"></p>"
                + "        <div class=\"flex items-center justify-between\">"
                + "          <div class=\"flex items-center gap-4\">"
                + "            <div class=\"w-10 h-10 rounded-2xl bg-gradient-to-br from-orange-400 to-orange-600 flex items-center justify-center text-white text-sm font-black rotate-3 group-hover:rotate-0 transition-transform\" x-text=\"(post.author||'A').charAt(0)\"></div>"
                + "            <div class=\"flex flex-col\">"
                + "              <span class=\"text-white text-sm font-bold\" x-text=\"post.author||'" + anon + "'\"></span>"
                + "              <span class=\"text-slate-500 text-[11px] font-bold tracking-tighter uppercase\" x-text=\"post.time||'JUST NOW'\"></span>"
                + "            </div>"
                + "          </div>"
                + "          <div class=\"flex items-center gap-5 text-slate-500\">"
                + "            <span class=\"flex items-center gap-2 text-xs font-bold\"><i class=\"fa-solid fa-fire text-orange-500/50\"></i><span x-text=\"post.likes||0\"></span></span>"
                + "            <span class=\"flex items-center gap-2 text-xs font-bold\"><i class=\"fa-regular fa-comments\"></i><span x-text=\"(post.comments||[]).length\"></span></span>"
                + "          </div>"
                + "        </div>"
                + "      </div>"
                + "    </div>"
                + "  </template>"
                + "</div>"
                + "<div x-show=\"$store.app.filteredPosts.length===0\" class=\"text-center py-24 bg-slate-900/50 rounded-[3rem] border border-dashed border-slate-800\">"
                + "  <div class=\"w-24 h-24 bg-slate-800 rounded-full flex items-center justify-center mx-auto mb-6\">"
                + "    <i class=\"fa fa-magnifying-glass text-4xl text-slate-600\"></i>"
                + "  </div>"
                + "  <p class=\"text-slate-500 font-black uppercase tracking-widest\">" + noResult + "</p>"
                + "</div>"
                + "</div>";
    }

    // ----- VIDEO / STREAMING -----
    private String buildVideoView(String lang) {
        boolean zh = lang.equals("ZH");
        String title = zh ? "&#x70ED;&#x95E8;&#x89C6;&#x9891;" : "Trending Videos";
        String noResult = zh ? "&#x6CA1;&#x6709;&#x76F8;&#x5173;&#x89C6;&#x9891;" : "No videos found";

        return "<div x-show=\"$store.app.activeTab === '#INDEX'\" class=\"p-6\">"
                + toast()
                + "<div class=\"flex items-center justify-between mb-8\">"
                + "  <div class=\"flex flex-col\">"
                + "    <h2 class=\"text-2xl font-black italic tracking-tighter uppercase text-white\">" + title + "</h2>"
                + "    <div class=\"h-1.5 w-12 bg-orange-500 mt-1 rounded-full\"></div>"
                + "  </div>"
                + "</div>"
                + "<div class=\"grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6\">"
                + "  <template x-for=\"video in $store.app.filteredPosts\" :key=\"video.id\">"
                + "    <div @click=\"$store.app.openPost(video)\" class=\"group cursor-pointer flex flex-col\">"
                + "      <div class=\"relative aspect-video bg-slate-900 rounded-xl overflow-hidden mb-3 border border-slate-700/50 hover:border-orange-500/50 transition-all\">"
                + "        <img :src=\"video.cover\" class=\"w-full h-full object-cover group-hover:scale-105 transition-transform duration-500\" />"
                + "        <div class=\"absolute inset-0 bg-black/20 group-hover:bg-black/0 transition-colors flex items-center justify-center\">"
                + "          <div class=\"w-12 h-12 bg-orange-500 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 scale-75 group-hover:scale-100 transition-all shadow-xl shadow-orange-500/20\">"
                + "            <i class=\"fa fa-play text-white ml-1\"></i>"
                + "          </div>"
                + "        </div>"
                + "        <div class=\"absolute bottom-2 right-2 bg-black/80 px-1.5 py-0.5 rounded text-[10px] font-bold text-white backdrop-blur\" x-text=\"video.duration\"></div>"
                + "        <div class=\"absolute top-2 left-2 bg-black/60 px-1.5 py-0.5 rounded text-[9px] font-black text-orange-400 uppercase tracking-tighter border border-orange-500/30\" x-text=\"video.quality\"></div>"
                + "      </div>"
                + "      <div class=\"flex gap-3\">"
                + "        <div class=\"w-9 h-9 flex-shrink-0 bg-slate-800 rounded-full flex items-center justify-center text-xs font-black border border-slate-700\" x-text=\"video.author.charAt(0)\"></div>"
                + "        <div class=\"flex flex-col\">"
                + "          <h3 class=\"text-white font-bold text-sm line-clamp-2 leading-snug group-hover:text-orange-400 transition-colors\" x-text=\"video.title\"></h3>"
                + "          <div class=\"flex flex-col mt-1 text-slate-500 text-[11px] font-medium\">"
                + "            <span class=\"hover:text-slate-300 transition-colors uppercase\" x-text=\"video.author\"></span>"
                + "            <span x-text=\"video.views + ' • ' + video.time\"></span>"
                + "          </div>"
                + "        </div>"
                + "      </div>"
                + "    </div>"
                + "  </template>"
                + "</div>"
                + "</div>";
    }

    // ----- E-COMMERCE -----
    private String buildEcommerceView(String lang) {
        boolean zh = lang.equals("ZH");
        String title = zh ? "&#x5546;&#x54C1;&#x5217;&#x8868;" : "Products";
        String addBtn = zh ? "&#x52A0;&#x5165;&#x8D2D;&#x7269;&#x8F66;" : "Add to Cart";
        String priceLabel = zh ? "&#x4EF7;&#x683C;: " : "Price: ";
        String stockLabel = zh ? "&#x5E93;&#x5B58;: " : "Stock: ";

        return "<div x-show=\"$store.app.activeTab === '#INDEX'\" class=\"p-6\">"
                + toast()
                + "<h2 class=\"text-xl font-bold text-white mb-6\">" + title + "</h2>"
                + "<div class=\"grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5\">"
                + "<template x-for=\"item in $store.app.filteredPosts\" :key=\"item.id\">"
                + "<div class=\"bg-slate-800/60 border border-slate-700/50 rounded-2xl p-5 hover:border-indigo-500/50 transition-all flex flex-col gap-3\">"
                + "<div class=\"w-full h-32 bg-slate-700 rounded-xl flex items-center justify-center text-slate-500\">"
                + "<i class=\"fa fa-image text-3xl\"></i></div>"
                + "<h3 class=\"text-white font-semibold\" x-text=\"item.title||item.name\"></h3>"
                + "<p class=\"text-slate-400 text-sm line-clamp-2\" x-text=\"item.description||item.content||''\"></p>"
                + "<div class=\"flex items-center justify-between mt-auto\">"
                + "<div>"
                + "<p class=\"text-indigo-400 font-bold text-lg\" x-text=\"'" + priceLabel + "' + (item.price || Math.floor(Math.random()*500+50) + '&#x5143;')\"></p>"
                + "<p class=\"text-slate-500 text-xs\" x-text=\"'" + stockLabel + "' + (item.stock || Math.floor(Math.random()*1000+10))\"></p>"
                + "</div>"
                + "<button @click=\"$store.app.addToCart(item)\""
                + " class=\"bg-indigo-600 hover:bg-indigo-500 active:scale-95 text-white px-3 py-2 rounded-lg text-sm transition-all\">"
                + "<i class=\"fa fa-cart-plus mr-1\"></i>" + addBtn + "</button>"
                + "</div></div></template>"
                + "<div x-show=\"$store.app.filteredPosts.length===0\" class=\"col-span-3 text-center py-16 text-slate-500\">"
                + "<i class=\"fa fa-box text-4xl mb-4 block\"></i><p>" + (zh ? "&#x6CA1;&#x6709;&#x5546;&#x54C1;" : "No products found") + "</p></div>"
                + "</div></div>";
    }

    // ----- SAAS DASHBOARD -----
    private String buildSaasView(String lang) {
        boolean zh = lang.equals("ZH");
        String title = zh ? "&#x8FD0;&#x8425;&#x6982;&#x89C8;" : "Overview";
        String newRecord = zh ? "&#x65B0;&#x5EFA;&#x8BB0;&#x5F55;" : "New Record";

        return "<div x-show=\"$store.app.activeTab === '#INDEX'\" class=\"p-6\">"
                + toast()
                + "<div class=\"flex items-center justify-between mb-6\">"
                + "<h2 class=\"text-xl font-bold text-white\">" + title + "</h2>"
                + "<button @click=\"$store.app.showPostModal=true\""
                + " class=\"bg-indigo-600 hover:bg-indigo-500 active:scale-95 text-white px-4 py-2 rounded-lg text-sm\">"
                + "<i class=\"fa fa-plus mr-1\"></i>" + newRecord + "</button></div>"
                + "<div class=\"grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8\">"
                + "<div class=\"bg-slate-800/60 border border-slate-700/50 rounded-2xl p-5 text-center\">"
                + "<p class=\"text-2xl font-bold text-indigo-400\" x-text=\"$store.app.posts.length\"></p>"
                + "<p class=\"text-slate-400 text-sm mt-1\">" + (zh ? "&#x603B;&#x8BB0;&#x5F55;" : "Total Records") + "</p></div>"
                + "<div class=\"bg-slate-800/60 border border-slate-700/50 rounded-2xl p-5 text-center\">"
                + "<p class=\"text-2xl font-bold text-green-400\">+12%</p>"
                + "<p class=\"text-slate-400 text-sm mt-1\">" + (zh ? "&#x672C;&#x6708;&#x589E;&#x957F;" : "Growth") + "</p></div>"
                + "<div class=\"bg-slate-800/60 border border-slate-700/50 rounded-2xl p-5 text-center\">"
                + "<p class=\"text-2xl font-bold text-cyan-400\" x-text=\"$store.app.posts.reduce(function(s,p){return s+(p.likes||0);},0)\"></p>"
                + "<p class=\"text-slate-400 text-sm mt-1\">" + (zh ? "&#x603B;&#x4E92;&#x52A8;&#x6570;" : "Total Interactions") + "</p></div>"
                + "<div class=\"bg-slate-800/60 border border-slate-700/50 rounded-2xl p-5 text-center\">"
                + "<p class=\"text-2xl font-bold text-amber-400\" x-text=\"$store.app.unreadCount\"></p>"
                + "<p class=\"text-slate-400 text-sm mt-1\">" + (zh ? "&#x672A;&#x5904;&#x7406;&#x6D88;&#x606F;" : "Pending") + "</p></div>"
                + "</div>"
                + "<div class=\"bg-slate-800/60 border border-slate-700/50 rounded-2xl overflow-hidden\">"
                + "<table class=\"w-full text-sm\">"
                + "<thead class=\"bg-slate-700/50\"><tr>"
                + "<th class=\"text-left text-slate-400 font-medium p-4\">" + (zh ? "&#x540D;&#x79F0;" : "Name") + "</th>"
                + "<th class=\"text-left text-slate-400 font-medium p-4\">" + (zh ? "&#x4F5C;&#x8005;" : "Author") + "</th>"
                + "<th class=\"text-right text-slate-400 font-medium p-4\">" + (zh ? "&#x72B6;&#x6001;" : "Status") + "</th>"
                + "</tr></thead>"
                + "<tbody>"
                + "<template x-for=\"item in $store.app.filteredPosts\" :key=\"item.id\">"
                + "<tr class=\"border-t border-slate-700/50 hover:bg-slate-700/30 transition-colors cursor-pointer\" @click=\"$store.app.openPost(item)\">"
                + "<td class=\"p-4 text-white\" x-text=\"item.title||item.name\"></td>"
                + "<td class=\"p-4 text-slate-400\" x-text=\"item.author||''\"></td>"
                + "<td class=\"p-4 text-right\"><span class=\"bg-green-500/20 text-green-400 text-xs px-2 py-1 rounded-full\">Active</span></td>"
                + "</tr></template></tbody>"
                + "</table></div></div>";
    }

    // ----- BOOKING / HEALTHCARE -----
    private String buildBookingView(String lang) {
        boolean zh = lang.equals("ZH");
        String title = zh ? "&#x9009;&#x62E9;&#x5C31;&#x8BCA;&#x9879;&#x76EE;" : "Available Services";
        String bookBtn = zh ? "&#x9884;&#x7EA6;&#x5C31;&#x8BCA;" : "Book Now";

        return "<div x-show=\"$store.app.activeTab === '#INDEX'\" class=\"p-6\">"
                + toast()
                + "<h2 class=\"text-xl font-bold text-white mb-6\">" + title + "</h2>"
                + "<div class=\"grid grid-cols-1 md:grid-cols-2 gap-5\">"
                + "<template x-for=\"item in $store.app.filteredPosts\" :key=\"item.id\">"
                + "<div class=\"bg-slate-800/60 border border-slate-700/50 rounded-2xl p-5 hover:border-indigo-500/50 transition-all\">"
                + "<div class=\"flex items-start gap-4\">"
                + "<div class=\"w-12 h-12 rounded-xl bg-indigo-600/20 flex items-center justify-center flex-shrink-0\">"
                + "<i class=\"fa fa-user-md text-indigo-400 text-xl\"></i></div>"
                + "<div class=\"flex-1\">"
                + "<h3 class=\"text-white font-semibold\" x-text=\"item.title||item.name\"></h3>"
                + "<p class=\"text-slate-400 text-sm mt-1\" x-text=\"item.description||item.content||''\"></p>"
                + "<p class=\"text-indigo-400 text-sm mt-2\" x-text=\"item.department||item.category||'&#x5168;&#x79D1;&#x5BA4;'\"></p>"
                + "</div></div>"
                + "<button @click=\"$store.app.bookAppointment ? $store.app.bookAppointment(item.title||item.name, '2025-04-01') : $store.app.toast('&#x9884;&#x7EA6;&#x6210;&#x529F;')\""
                + " class=\"mt-4 w-full bg-indigo-600 hover:bg-indigo-500 active:scale-95 text-white py-2 rounded-lg text-sm transition-all\">"
                + bookBtn + "</button>"
                + "</div></template>"
                + "<div x-show=\"$store.app.filteredPosts.length===0\" class=\"col-span-2 text-center py-16 text-slate-500\">"
                + "<i class=\"fa fa-calendar text-4xl mb-4 block\"></i><p>" + (zh ? "&#x6682;&#x65E0;&#x9879;&#x76EE;" : "No services") + "</p></div>"
                + "</div></div>";
    }

    // ----- EDUCATION -----
    private String buildEducationView(String lang) {
        boolean zh = lang.equals("ZH");
        String title = zh ? "&#x8BFE;&#x7A0B;&#x5217;&#x8868;" : "All Courses";
        String enrollBtn = zh ? "&#x7ACB;&#x5373;&#x62A5;&#x540D;" : "Enroll";
        String enrolledLabel = zh ? "&#x5DF2;&#x62A5;&#x540D;" : "Enrolled";

        return "<div x-show=\"$store.app.activeTab === '#INDEX'\" class=\"p-6\">"
                + toast()
                + "<h2 class=\"text-xl font-bold text-white mb-6\">" + title + "</h2>"
                + "<div class=\"grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5\">"
                + "<template x-for=\"item in $store.app.filteredPosts\" :key=\"item.id\">"
                + "<div class=\"bg-slate-800/60 border border-slate-700/50 rounded-2xl overflow-hidden hover:border-indigo-500/50 transition-all group\">"
                + "<div class=\"h-28 bg-gradient-to-br from-indigo-600/30 to-cyan-600/30 flex items-center justify-center\">"
                + "<i class=\"fa fa-play-circle text-4xl text-indigo-400\"></i></div>"
                + "<div class=\"p-4\">"
                + "<div class=\"flex flex-wrap gap-1 mb-2\">"
                + "<template x-for=\"tag in (item.tags||[])\" :key=\"tag\">"
                + "<span class=\"bg-indigo-600/20 text-indigo-400 text-xs px-1.5 py-0.5 rounded\" x-text=\"tag\"></span>"
                + "</template></div>"
                + "<h3 class=\"text-white font-semibold mb-1 group-hover:text-indigo-300 transition-colors\" x-text=\"item.title||item.name\"></h3>"
                + "<p class=\"text-slate-400 text-sm mb-3 line-clamp-2\" x-text=\"item.description||item.content||''\"></p>"
                + "<div class=\"flex items-center justify-between\">"
                + "<span class=\"text-slate-400 text-xs\" x-text=\"item.author||''\"></span>"
                + "<button @click=\"$store.app.enroll ? $store.app.enroll(item) : $store.app.toast('&#x62A5;&#x540D;&#x6210;&#x529F;')\""
                + " :class=\"$store.app.isEnrolled && $store.app.isEnrolled(item) ? 'bg-green-600' : 'bg-indigo-600 hover:bg-indigo-500'\""
                + " class=\"active:scale-95 text-white px-3 py-1.5 rounded-lg text-xs transition-all\">"
                + "<span x-text=\"$store.app.isEnrolled && $store.app.isEnrolled(item) ? '" + enrolledLabel + "' : '" + enrollBtn + "'\"></span>"
                + "</button></div></div></div></template>"
                + "<div x-show=\"$store.app.filteredPosts.length===0\" class=\"col-span-3 text-center py-16 text-slate-500\">"
                + "<i class=\"fa fa-book text-4xl mb-4 block\"></i><p>" + (zh ? "&#x6CA1;&#x6709;&#x8BFE;&#x7A0B;" : "No courses") + "</p></div>"
                + "</div></div>";
    }

    private String buildDetailView(String lang, Industry industry) {
        boolean zh = lang.equals("ZH");
        String back = zh ? "&#x8FD4;&#x56DE;" : "Back";
        String noContent = zh ? "&#xFF08;&#x6682;&#x65E0;&#x5185;&#x5BB9;&#xFF09;" : "(No content)";
        String comments = zh ? "&#x8BC4;&#x8BBA;" : "Comments";
        String commentPH = zh ? "&#x5199;&#x4E0B;&#x8BC4;&#x8BBA;..." : "Write a comment...";
        String loginFirst = zh ? "&#x767B;&#x5F55;&#x540E;&#x8BC4;&#x8BBA;" : "Login to comment";
        String submit = zh ? "&#x53D1;&#x8868;" : "Submit";
        String empty = zh ? "&#x6682;&#x65E0;&#x8BC4;&#x8BBA;" : "No comments yet";
        String anon = zh ? "&#x533F;&#x540D;" : "Anonymous";

        // For e-commerce, detail view is a product page with "add to cart"
        String actionButton = "";
        if (industry == Industry.ECOMMERCE) {
            actionButton = "<button @click=\"$store.app.addToCart ? $store.app.addToCart($store.app.selectedPost) : $store.app.toast('&#x5DF2;&#x52A0;&#x5165;&#x8D2D;&#x7269;&#x8F66;')\""
                    + " class=\"bg-indigo-600 hover:bg-indigo-500 active:scale-95 text-white px-6 py-2 rounded-lg text-sm transition-all\">"
                    + "<i class=\"fa fa-cart-plus mr-1\"></i>" + (zh ? "&#x52A0;&#x5165;&#x8D2D;&#x7269;&#x8F66;" : "Add to Cart")
                    + "</button>";
        } else if (industry == Industry.BOOKING) {
            actionButton = "<button @click=\"$store.app.bookAppointment ? $store.app.bookAppointment($store.app.selectedPost?.title, '2025-04-01') : $store.app.toast('&#x9884;&#x7EA6;&#x6210;&#x529F;')\""
                    + " class=\"bg-green-600 hover:bg-green-500 active:scale-95 text-white px-6 py-2 rounded-lg text-sm transition-all\">"
                    + "<i class=\"fa fa-calendar-check mr-1\"></i>" + (zh ? "&#x7ACB;&#x5373;&#x9884;&#x7EA6;" : "Book Now")
                    + "</button>";
        }

        return "<div x-show=\"$store.app.activeTab === '#PostDetail'\" class=\"p-6 max-w-3xl mx-auto\">"
                + "<button @click=\"$store.app.backToFeed()\" class=\"text-slate-400 hover:text-white flex items-center gap-2 mb-6 text-sm transition-colors active:scale-95\">"
                + "<i class=\"fa fa-arrow-left\"></i> " + back + "</button>"
                + "<template x-if=\"$store.app.selectedPost\">"
                + "<div>"
                + "<div class=\"mb-6\">"
                + "<div class=\"flex flex-wrap gap-2 mb-3\">"
                + "<template x-for=\"tag in ($store.app.selectedPost.tags||[])\">"
                + "<span class=\"bg-indigo-600/20 text-indigo-400 text-xs px-2 py-1 rounded-full\" x-text=\"tag\"></span>"
                + "</template></div>"
                + "<h1 class=\"text-2xl font-bold text-white mb-3\" x-text=\"$store.app.selectedPost.title||$store.app.selectedPost.name||'&#x8BE6;&#x60C5;'\"></h1>"
                + "<div class=\"flex flex-wrap items-center gap-3\">"
                + "<div class=\"flex items-center gap-2 text-slate-400 text-sm\">"
                + "<span class=\"w-7 h-7 rounded-full bg-indigo-600 flex items-center justify-center text-xs font-bold\" x-text=\"($store.app.selectedPost.author||'?').charAt(0)\"></span>"
                + "<span x-text=\"$store.app.selectedPost.author||'" + anon + "'\"></span>"
                + "</div>"
                + "<button @click=\"$store.app.likePost($store.app.selectedPost)\""
                + " :class=\"$store.app.selectedPost.liked?'text-red-400 bg-red-400/10':'text-slate-400 bg-slate-700'\""
                + " class=\"flex items-center gap-2 px-3 py-1.5 rounded-lg transition-all active:scale-95 text-sm\">"
                + "<i :class=\"$store.app.selectedPost.liked?'fa-solid fa-heart':'fa-regular fa-heart'\"></i>"
                + "<span x-text=\"$store.app.selectedPost.likes||0\"></span></button>"
                + actionButton
                + "</div></div>"
                + "<div class=\"bg-slate-800/60 rounded-2xl p-6 mb-8 text-slate-300 leading-relaxed border border-slate-700/50\">"
                + "<p x-text=\"$store.app.selectedPost.content||$store.app.selectedPost.description||$store.app.selectedPost.summary||'" + noContent + "'\"></p>"
                + "</div>"
                + "<h3 class=\"text-white font-semibold mb-4 flex items-center gap-2\">"
                + "<i class=\"fa-regular fa-comments text-indigo-400\"></i> "
                + comments + " (<span x-text=\"($store.app.selectedPost.comments||[]).length\"></span>)</h3>"
                + "<div class=\"bg-slate-800/60 rounded-xl p-4 mb-6 border border-slate-700/50\">"
                + "<textarea x-model=\"$store.app.commentDraft[$store.app.selectedPost.id]\""
                + " :placeholder=\"$store.app.isLoggedIn?'" + commentPH + "':'" + loginFirst + "'\""
                + " rows=\"3\" class=\"w-full bg-slate-700/50 text-white rounded-lg p-3 text-sm resize-none border border-slate-600 focus:border-indigo-500 outline-none\"></textarea>"
                + "<div class=\"flex justify-end mt-2\">"
                + "<button @click=\"$store.app.submitComment($store.app.selectedPost)\" class=\"bg-indigo-600 hover:bg-indigo-500 active:scale-95 text-white px-4 py-2 rounded-lg text-sm transition-all\">"
                + submit + "</button></div></div>"
                + "<div class=\"space-y-4\">"
                + "<template x-for=\"comment in ($store.app.selectedPost.comments||[])\" :key=\"comment.id\">"
                + "<div class=\"flex gap-3\">"
                + "<span class=\"w-8 h-8 rounded-full bg-indigo-700 flex items-center justify-center text-xs font-bold flex-shrink-0\" x-text=\"(comment.author||'?').charAt(0)\"></span>"
                + "<div class=\"flex-1 bg-slate-800/60 rounded-xl p-3 border border-slate-700/50\">"
                + "<div class=\"flex justify-between mb-1\">"
                + "<span class=\"text-slate-300 text-sm\" x-text=\"comment.author\"></span>"
                + "<span class=\"text-slate-500 text-xs\" x-text=\"comment.time\"></span></div>"
                + "<p class=\"text-slate-400 text-sm\" x-text=\"comment.text\"></p>"
                + "</div></div></template>"
                + "<div x-show=\"($store.app.selectedPost.comments||[]).length===0\" class=\"text-center py-8 text-slate-500\">"
                + "<i class=\"fa-regular fa-comment-dots text-2xl mb-2 block\"></i><p class=\"text-sm\">" + empty + "</p></div>"
                + "</div>"
                + "</div></template></div>";
    }

    // ==========================================
    // DETAIL VIEW (industry-aware)
    // ==========================================

    private String buildProfileView(String lang) {
        boolean zh = lang.equals("ZH");
        String loginPrompt = zh ? "&#x767B;&#x5F55;&#x540E;&#x67E5;&#x770B;&#x4E2A;&#x4EBA;&#x4E2D;&#x5FC3;" : "Login to view profile";
        String loginBtn = zh ? "&#x767B;&#x5F55;" : "Login";
        String myPosts = zh ? "&#x6211;&#x7684;&#x5185;&#x5BB9;" : "My Content";
        String noPosts = zh ? "&#x8FD8;&#x6CA1;&#x6709;&#x5185;&#x5BB9;" : "Nothing yet";
        String logout = zh ? "&#x9000;&#x51FA;&#x767B;&#x5F55;" : "Logout";

        return "<div x-show=\"$store.app.activeTab === '#Profile'\" class=\"p-6 max-w-2xl mx-auto\">"
                + "<div x-show=\"!$store.app.isLoggedIn\" class=\"text-center py-16\">"
                + "<i class=\"fa fa-user-circle text-6xl text-slate-600 mb-4 block\"></i>"
                + "<p class=\"text-slate-400 mb-4\">" + loginPrompt + "</p>"
                + "<button @click=\"$store.app.showAuthModal=true\" class=\"bg-indigo-600 hover:bg-indigo-500 text-white px-6 py-2 rounded-lg text-sm active:scale-95 transition-all\">"
                + loginBtn + "</button></div>"
                + "<div x-show=\"$store.app.isLoggedIn\">"
                + "<div class=\"bg-slate-800/60 rounded-2xl p-8 border border-slate-700/50 mb-6 text-center\">"
                + "<div class=\"w-20 h-20 rounded-full bg-indigo-600 flex items-center justify-center text-2xl font-bold mx-auto mb-4\""
                + " x-text=\"$store.app.currentUser?.avatar||'&#x1F464;'\"></div>"
                + "<h2 class=\"text-white text-xl font-bold\" x-text=\"$store.app.currentUser?.name\"></h2>"
                + "<p class=\"text-slate-400 text-sm mt-1\" x-text=\"$store.app.currentUser?.bio||''\"></p></div>"
                + "<div class=\"bg-slate-800/60 rounded-2xl p-6 border border-slate-700/50\">"
                + "<h3 class=\"text-white font-semibold mb-4\">" + myPosts + "</h3>"
                + "<div class=\"space-y-3\">"
                + "<template x-for=\"post in $store.app.posts.filter(function(p){return p.author===$store.app.currentUser?.name;})\" :key=\"post.id\">"
                + "<div class=\"flex items-center justify-between py-2 border-b border-slate-700/50 cursor-pointer hover:text-indigo-300 transition-colors\" @click=\"$store.app.openPost(post)\">"
                + "<span class=\"text-slate-300 text-sm\" x-text=\"post.title||post.name\"></span>"
                + "<div class=\"flex items-center gap-3 text-slate-500 text-xs\">"
                + "<span><i class=\"fa fa-heart mr-1\"></i><span x-text=\"post.likes||0\"></span></span>"
                + "<span><i class=\"fa fa-eye mr-1\"></i><span x-text=\"post.views||0\"></span></span>"
                + "</div></div></template>"
                + "<div x-show=\"$store.app.posts.filter(function(p){return p.author===$store.app.currentUser?.name;}).length===0\" class=\"text-slate-500 text-sm text-center py-4\">"
                + noPosts + "</div></div></div>"
                + "<div class=\"mt-4 text-center\">"
                + "<button @click=\"$store.app.logout()\" class=\"text-slate-400 hover:text-red-400 text-sm transition-colors\">"
                + "<i class=\"fa fa-sign-out-alt mr-1\"></i>" + logout + "</button></div></div></div>";
    }

    private String generateFeaturePage(ProjectManifest manifest, String routeId, String routeName, String lang) {
        String systemPrompt = "Design a VISUAL feature panel for: " + routeName + "\n"
                + "RULES:\n"
                + "1. Root: <div x-show=\"$store.app.activeTab === '#" + routeId + "'\" class=\"p-6\">\n"
                + "2. NO x-data, NO Alpine store definitions. NO event handler logic.\n"
                + "3. For interactive buttons use: @click=\"$store.app.toast('...')\"\n"
                + "4. Tailwind CSS. Dark slate theme. Glassmorphism. Real mock content.\n"
                + "5. Output raw HTML in ```html.";
        try {
            return parseHtmlSnippet(llmClient.chat(systemPrompt, "Feature: " + routeName + " | App: " + manifest.getUserIntent()));
        } catch (IOException e) {
            return "<!-- Error: " + routeName + " -->";
        }
    }

    // ==========================================
    // LLM FEATURE PAGE (visual only, generic)
    // ==========================================

    private String generateFallbackMockData(Industry industry, String intent) {
        try {
            ClassPathResource resource = new ClassPathResource("static/prototype/mock-fallback.json");
            String jsonContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // 使用 Jackson 解析（如果是 Spring 环境，ObjectMapper 通常可用）
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(jsonContent);

            String industryKey = industry.name();
            com.fasterxml.jackson.databind.JsonNode industryNode = rootNode.get(industryKey);

            if (industryNode != null && industryNode.isArray()) {
                return industryNode.toString();
            }

            // 如果没找到对应的行业，尝试使用 COMMUNITY 作为默认
            com.fasterxml.jackson.databind.JsonNode defaultNode = rootNode.get("COMMUNITY");
            return defaultNode != null ? defaultNode.toString() : "[]";

        } catch (Exception e) {
            log.error("[UiDesigner] Failed to load fallback mock data", e);
            return "[]";
        }
    }

    // ==========================================
    // FALLBACK MOCK DATA GENERATOR
    // 从外部 JSON 资源文件读取对应行业的模拟数据
    // ==========================================

    /**
     * Reusable toast div - always shows $store.app toastMessage
     */
    private String toast() {
        return "<div x-show=\"$store.app.toastVisible\" x-transition"
                + " class=\"fixed bottom-6 right-6 z-50 bg-indigo-600 text-white px-5 py-3 rounded-xl shadow-2xl text-sm font-medium\""
                + " x-text=\"$store.app.toastMessage\"></div>";
    }

    // ==========================================
    // UTILITIES
    // ==========================================

    private String injectStore(String html, String storeScript) {
        int idx = html.toLowerCase().indexOf("</head>");
        if (idx != -1) return html.substring(0, idx) + storeScript + "\n" + html.substring(idx);
        int bodyIdx = html.toLowerCase().indexOf("</body>");
        if (bodyIdx != -1) return html.substring(0, bodyIdx) + storeScript + "\n" + html.substring(bodyIdx);
        return storeScript + "\n" + html;
    }

    private String assemble(String shell, String slots) {
        if (shell.contains("{{CONTENT_SLOTS}}")) return shell.replace("{{CONTENT_SLOTS}}", slots);
        return shell + "\n" + slots;
    }

    private String parseHtmlSnippet(String response) {
        if (response == null) return "";
        int start = response.indexOf("```html");
        if (start != -1) {
            start += 7;
            int end = response.lastIndexOf("```");
            if (end > start) return response.substring(start, end).trim();
        }
        if (response.trim().startsWith("<")) return response.trim();
        return response;
    }

    private List<String> parseFeatureNodes(String mindMap) {
        if (mindMap == null || mindMap.trim().isEmpty()) return new ArrayList<>();
        List<String> nodes = new ArrayList<>();
        for (String line : mindMap.split("\\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("```") && t.startsWith("- ")) nodes.add(t.substring(2).trim());
        }
        return nodes;
    }

    private String encodeRouteId(String name) {
        return java.net.URLEncoder.encode(name.replace(" ", ""), StandardCharsets.UTF_8);
    }

    private String getThemePalette(String intent) {
        String input = intent.toLowerCase();
        if (input.contains("pornhub") || (input.contains("black") && input.contains("orange"))) {
            return "bg: #000000, secondary: #1a1a1a, accent: #ff9900 (Orange), text: white";
        }
        if (input.contains("retro") || input.contains("vintage")) {
            return "bg: #fdf6e3, secondary: #eee8d5, accent: #b58900 (Gold), text: #586e75";
        }
        if (input.contains("nature") || input.contains("green")) {
            return "bg: #064e3b, secondary: #065f46, accent: #10b981 (Emerald), text: white";
        }
        if (input.contains("minimal") || input.contains("white")) {
            return "bg: #f8fafc, secondary: #f1f5f9, accent: #0f172a (Slate), text: #1e293b";
        }
        if (input.contains("vibrant") || input.contains("purple")) {
            return "bg: #2e1065, secondary: #4c1d95, accent: #d946ef (Fuchsia), text: white";
        }
        // DEFAULT: Premium Dark Slate
        return "bg: #0f172a, secondary: #1e293b, accent: #6366f1 (Indigo), text: white";
    }

    // ==========================================
    // THEME ENGINE
    // Detects color palette based on user intent
    // ==========================================

    private String getThemeColors(String intent) {
        String input = intent.toLowerCase();
        if (input.contains("pornhub") || (input.contains("black") && input.contains("orange"))) {
            return "bg-black text-white selection:bg-orange-500";
        }
        if (input.contains("minimal") || input.contains("white")) {
            return "bg-slate-100 text-slate-900 selection:bg-slate-200";
        }
        return "bg-slate-900 text-white selection:bg-indigo-500";
    }

    private enum Industry {
        COMMUNITY, ECOMMERCE, SAAS, SOCIAL, BOOKING, EDUCATION, FINANCE, VIDEO, DEFAULT
    }
}
