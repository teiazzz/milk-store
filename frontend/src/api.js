const request = async (url, options = {}) => {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: '服务暂时不可用' }))
    throw new Error(error.message || '请求失败')
  }
  return response.json()
}

export const mallApi = {
  getProducts: (keyword = '', category = '') => {
    const params = new URLSearchParams()
    if (keyword) params.set('keyword', keyword)
    if (category) params.set('category', category)
    return request(`/api/products?${params}`)
  },
  getCategories: () => request('/api/categories'),
  getOrders: () => request('/api/orders'),
  createOrder: (payload) => request('/api/orders', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
