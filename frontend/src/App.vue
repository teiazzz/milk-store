<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { mallApi } from './api'

const products = ref([])
const categories = ref([])
const orders = ref([])
const loading = ref(true)
const page = ref('catalog')
const keyword = ref('')
const activeCategory = ref('')
const cartOpen = ref(false)
const checkoutOpen = ref(false)
const submitting = ref(false)
const notice = ref('')
const errorMessage = ref('')
const customer = reactive({ customerName: '', phone: '', address: '' })
const cart = ref(JSON.parse(localStorage.getItem('novamall-cart') || '[]'))

const cartCount = computed(() => cart.value.reduce((sum, item) => sum + item.quantity, 0))
const cartTotal = computed(() => cart.value.reduce((sum, item) => sum + item.price * item.quantity, 0))

watch(cart, value => localStorage.setItem('novamall-cart', JSON.stringify(value)), { deep: true })

const loadProducts = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    products.value = await mallApi.getProducts(keyword.value, activeCategory.value)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

const selectCategory = category => {
  activeCategory.value = category
  loadProducts()
}

const addToCart = product => {
  const item = cart.value.find(entry => entry.id === product.id)
  if (item) {
    if (item.quantity < product.stock) item.quantity += 1
  } else {
    cart.value.push({ ...product, quantity: 1 })
  }
  notice.value = `${product.name} 已加入购物车`
  setTimeout(() => (notice.value = ''), 1800)
}

const updateQuantity = (item, delta) => {
  item.quantity = Math.max(1, Math.min(item.stock, item.quantity + delta))
}

const removeFromCart = id => {
  cart.value = cart.value.filter(item => item.id !== id)
}

const openCheckout = () => {
  if (!cart.value.length) return
  cartOpen.value = false
  checkoutOpen.value = true
}

const submitOrder = async () => {
  submitting.value = true
  errorMessage.value = ''
  try {
    const order = await mallApi.createOrder({
      ...customer,
      items: cart.value.map(item => ({ productId: item.id, quantity: item.quantity }))
    })
    cart.value = []
    checkoutOpen.value = false
    Object.assign(customer, { customerName: '', phone: '', address: '' })
    notice.value = `下单成功，订单号 ${order.orderNo}`
    await loadProducts()
    await loadOrders()
    setTimeout(() => (notice.value = ''), 3500)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    submitting.value = false
  }
}

const loadOrders = async () => {
  try {
    orders.value = await mallApi.getOrders()
  } catch (error) {
    errorMessage.value = error.message
  }
}

const switchPage = async target => {
  page.value = target
  cartOpen.value = false
  if (target === 'orders') await loadOrders()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const money = value => `¥${Number(value).toFixed(2)}`
const formatDate = value => new Date(value).toLocaleString('zh-CN', { hour12: false })

onMounted(async () => {
  try {
    categories.value = await mallApi.getCategories()
    await loadProducts()
  } catch (error) {
    errorMessage.value = error.message
  }
})
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <button class="brand" @click="switchPage('catalog')">
        <span class="brand-mark">N</span>
        <span><strong>NovaMall</strong><small>星选好物，点亮日常</small></span>
      </button>
      <nav>
        <button :class="{ active: page === 'catalog' }" @click="switchPage('catalog')">发现好物</button>
        <button :class="{ active: page === 'orders' }" @click="switchPage('orders')">我的订单</button>
      </nav>
      <button class="cart-trigger" @click="cartOpen = true">
        <span>购物车</span><b>{{ cartCount }}</b>
      </button>
    </header>

    <main v-if="page === 'catalog'">
      <section class="hero">
        <div class="hero-copy">
          <span class="eyebrow">NEW SEASON · 2026</span>
          <h1>把喜欢的生活，<br><em>装进购物车。</em></h1>
          <p>严选数码、家居与潮流单品。简洁的浏览体验，可靠的 Spring Boot 订单服务。</p>
          <a href="#catalog" class="primary-link">开始选购 <span>→</span></a>
        </div>
        <div class="hero-art">
          <div class="orbit orbit-one"></div>
          <div class="orbit orbit-two"></div>
          <div class="floating-card card-a"><span>本周精选</span><b>轻盈好物</b></div>
          <div class="floating-card card-b"><span>全场</span><b>品质保障</b></div>
          <div class="hero-badge"><strong>8</strong><span>款首发<br>精选单品</span></div>
        </div>
      </section>

      <section id="catalog" class="catalog-section">
        <div class="section-heading">
          <div><span class="eyebrow">CURATED COLLECTION</span><h2>本期精选</h2></div>
          <form class="search-box" @submit.prevent="loadProducts">
            <input v-model.trim="keyword" placeholder="搜索商品或描述" />
            <button>搜索</button>
          </form>
        </div>

        <div class="category-tabs">
          <button :class="{ active: activeCategory === '' }" @click="selectCategory('')">全部</button>
          <button v-for="category in categories" :key="category"
                  :class="{ active: activeCategory === category }" @click="selectCategory(category)">
            {{ category }}
          </button>
        </div>

        <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>
        <div v-if="loading" class="loading-state">正在为你挑选好物...</div>
        <div v-else-if="!products.length" class="empty-state">没有找到符合条件的商品</div>
        <div v-else class="product-grid">
          <article v-for="product in products" :key="product.id" class="product-card">
            <div class="product-image">
              <img :src="product.imageUrl" :alt="product.name" />
              <span v-if="product.featured" class="featured-tag">精选</span>
              <span class="stock-tag">库存 {{ product.stock }}</span>
            </div>
            <div class="product-info">
              <span class="product-category">{{ product.category }}</span>
              <h3>{{ product.name }}</h3>
              <p>{{ product.description }}</p>
              <div class="product-actions">
                <strong>{{ money(product.price) }}</strong>
                <button :disabled="product.stock === 0" @click="addToCart(product)">
                  {{ product.stock === 0 ? '已售罄' : '加入购物车' }}
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="service-strip">
        <div><b>01</b><span><strong>品质严选</strong><small>每件商品认真筛选</small></span></div>
        <div><b>02</b><span><strong>库存校验</strong><small>后端实时保障库存</small></span></div>
        <div><b>03</b><span><strong>快捷下单</strong><small>清晰简单的购买流程</small></span></div>
      </section>
    </main>

    <main v-else class="orders-page">
      <div class="orders-heading"><span class="eyebrow">ORDER CENTER</span><h1>我的订单</h1><p>查看最近提交的商城订单与商品明细。</p></div>
      <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>
      <div v-if="!orders.length" class="empty-state large">暂时还没有订单，去挑一件喜欢的商品吧。</div>
      <article v-for="order in orders" :key="order.orderNo" class="order-card">
        <div class="order-top">
          <div><small>订单号</small><strong>{{ order.orderNo }}</strong></div>
          <div><small>下单时间</small><span>{{ formatDate(order.createdAt) }}</span></div>
          <span class="status-pill">{{ order.status }}</span>
        </div>
        <div class="order-items">
          <div v-for="item in order.items" :key="item.productId" class="order-item">
            <img :src="item.imageUrl" :alt="item.productName" />
            <span><strong>{{ item.productName }}</strong><small>{{ money(item.unitPrice) }} × {{ item.quantity }}</small></span>
            <b>{{ money(item.subtotal) }}</b>
          </div>
        </div>
        <div class="order-bottom"><span>收货人：{{ order.customerName }} · {{ order.phone }}</span><strong>实付 {{ money(order.totalAmount) }}</strong></div>
      </article>
    </main>

    <footer><div class="brand footer-brand"><span class="brand-mark">N</span><span><strong>NovaMall</strong><small>Java + Vue 全栈商城</small></span></div><p>© 2026 NovaMall. Built with Spring Boot & Vue.</p></footer>

    <div v-if="cartOpen" class="overlay" @click.self="cartOpen = false">
      <aside class="cart-drawer">
        <div class="drawer-head"><div><span class="eyebrow">YOUR CART</span><h2>购物车</h2></div><button @click="cartOpen = false">×</button></div>
        <div v-if="!cart.length" class="empty-state">购物车还是空的</div>
        <div v-else class="cart-list">
          <div v-for="item in cart" :key="item.id" class="cart-item">
            <img :src="item.imageUrl" :alt="item.name" />
            <div><strong>{{ item.name }}</strong><small>{{ money(item.price) }}</small><div class="quantity"><button @click="updateQuantity(item, -1)">−</button><span>{{ item.quantity }}</span><button @click="updateQuantity(item, 1)">＋</button></div></div>
            <button class="remove" @click="removeFromCart(item.id)">移除</button>
          </div>
        </div>
        <div class="cart-summary"><span>共 {{ cartCount }} 件商品</span><strong>{{ money(cartTotal) }}</strong></div>
        <button class="checkout-button" :disabled="!cart.length" @click="openCheckout">填写收货信息</button>
      </aside>
    </div>

    <div v-if="checkoutOpen" class="overlay modal-overlay" @click.self="checkoutOpen = false">
      <form class="checkout-modal" @submit.prevent="submitOrder">
        <button type="button" class="modal-close" @click="checkoutOpen = false">×</button>
        <span class="eyebrow">CHECKOUT</span><h2>确认订单</h2><p>请填写真实的收货信息以完成演示下单。</p>
        <label>收货人<input v-model.trim="customer.customerName" required maxlength="80" placeholder="请输入姓名" /></label>
        <label>手机号码<input v-model.trim="customer.phone" required pattern="1[0-9]{10}" placeholder="11 位手机号码" /></label>
        <label>收货地址<textarea v-model.trim="customer.address" required maxlength="255" placeholder="省 / 市 / 区 / 详细地址"></textarea></label>
        <div class="checkout-total"><span>订单金额</span><strong>{{ money(cartTotal) }}</strong></div>
        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
        <button class="checkout-button" :disabled="submitting">{{ submitting ? '正在提交...' : '提交订单' }}</button>
      </form>
    </div>

    <transition name="toast"><div v-if="notice" class="toast">✓ {{ notice }}</div></transition>
  </div>
</template>
