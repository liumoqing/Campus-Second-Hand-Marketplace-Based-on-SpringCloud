import { get, post, put, del } from './http'
import type {
  AddressItem,
  CategoryNode,
  CommentItem,
  ConversationItem,
  ChatMessage,
  LoginResult,
  NoticeItem,
  OrderItem,
  PageResult,
  ProductCard,
  ProductDetail,
  SellerCard,
  UserInfo
} from '@/types'

/* ---------------- A 认证 ---------------- */
export const authApi = {
  captcha: () => get<{ captchaKey: string; imageBase64: string }>('/auth/captcha'),
  register: (data: {
    avatar: string
    username: string
    email: string
    password: string
    confirmPassword: string
    nickname?: string
    captchaKey: string
    captchaCode: string
  }) => post<void>('/auth/register', data),
  login: (data: { account: string; password: string; captchaKey: string; captchaCode: string }) =>
    post<LoginResult>('/auth/login', data),
  refresh: (refreshToken: string) =>
    post<{ accessToken: string; refreshToken: string }>('/auth/refresh', { refreshToken }),
  logout: (refreshToken?: string) => post<void>('/auth/logout', { refreshToken }),
  me: () => get<UserInfo>('/auth/me'),
  uploadAvatar: async (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return post<{ url: string }>('/auth/avatar', fd)
  },
  changePassword: (data: { oldPassword: string; password: string; confirmPassword: string }) =>
    put<void>('/auth/password', data),
  resetCode: (email: string) =>
    post<{ sent: boolean; expireSeconds: number; devCode?: string }>('/auth/reset-code', { email }),
  resetPassword: (data: { email: string; code: string; newPassword: string; confirmPassword: string }) =>
    post<void>('/auth/reset-password', data)
}

/* ---------------- U 用户 ---------------- */
export const userApi = {
  profile: () => get<UserInfo>('/user/profile'),
  updateProfile: (data: Partial<UserInfo> & { nickname?: string }) => put<void>('/user/profile', data),
  sellerCard: (id: string) => get<SellerCard>(`/user/${id}`),
  statistics: () =>
    get<{
      favoriteCount: number
      onSaleCount: number
      draftCount: number
      soldCount: number
      boughtCount: number
      pendingShipCount: number
      pendingReceiveCount: number
      unreadCount: number
    }>('/user/statistics'),
  dict: () => get<Record<string, any>>('/user/dict'),
  addresses: () => get<AddressItem[]>('/user/addresses'),
  createAddress: (data: Partial<AddressItem>) => post<{ id: string }>('/user/addresses', data),
  updateAddress: (id: string, data: Partial<AddressItem>) => put<void>(`/user/addresses/${id}`, data),
  deleteAddress: (id: string) => del<void>(`/user/addresses/${id}`),
  setDefaultAddress: (id: string) => put<void>(`/user/addresses/${id}/default`)
}

/* ---------------- P 商品 ---------------- */
export const productApi = {
  page: (params: {
    keyword?: string
    categoryId?: string
    city?: string
    sort?: string
    conditionLevel?: number
    priceMin?: number
    priceMax?: number
    pageNum?: number
    pageSize?: number
  }) => get<PageResult<ProductCard>>('/product/page', params),
  detail: (id: string) => get<ProductDetail>(`/product/${id}`),
  similar: (id: string) => get<ProductCard[]>(`/product/${id}/similar`),
  create: (data: any) => post<{ productId: string; status: string }>('/product', data),
  update: (id: string, data: any) => put<void>(`/product/${id}`, data),
  remove: (id: string) => del<void>(`/product/${id}`),
  offShelf: (id: string, reason?: string) => put<void>(`/product/${id}/off-shelf`, { reason }),
  onShelf: (id: string) => put<void>(`/product/${id}/on-shelf`),
  publishDraft: (id: string) => put<void>(`/product/${id}/publish`),
  myPage: (params: { status?: string; pageNum?: number; pageSize?: number }) =>
    get<PageResult<ProductCard>>('/product/my/page', params),
  dict: () => get<Record<string, any>>('/product/dict'),
  comments: (id: string, params: { pageNum?: number; pageSize?: number; sort?: string }) =>
    get<PageResult<CommentItem>>(`/product/${id}/comments`, params),
  addComment: (id: string, data: { content: string; images?: string[]; parentId?: string }) =>
    post<void>(`/product/${id}/comments`, data),
  deleteComment: (id: string) => del<void>(`/product/comments/${id}`),
  categories: (admin = false) => get<CategoryNode[]>(admin ? '/category/admin/tree' : '/category/tree'),
  createCategory: (data: { name: string; parentId?: string; icon?: string; sort?: number }) =>
    post<void>('/category/admin', data),
  updateCategory: (id: string, data: { name?: string; icon?: string; sort?: number; status?: number }) =>
    put<void>(`/category/admin/${id}`, data),
  deleteCategory: (id: string) => del<void>(`/category/admin/${id}`),
  favorite: (id: string) => post<void>(`/favorite/${id}`),
  unfavorite: (id: string) => del<void>(`/favorite/${id}`),
  favoritePage: (params: { pageNum?: number; pageSize?: number }) =>
    get<PageResult<ProductCard>>('/favorite/page', params),
  upload: async (file: File, bizType = 'product') => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('bizType', bizType)
    return post<{ url: string }>('/product/upload', fd)
  }
}

/* ---------------- O 订单 ---------------- */
export const orderApi = {
  create: (data: { productId: string; addressId: string; remark?: string }) =>
    post<{
      orderNo: string
      totalAmount: string
      freight: string
      payAmount: string
      status: string
      sameCity: boolean
    }>('/order/create', data),
  page: (params: { role?: string; status?: string; pageNum?: number; pageSize?: number }) =>
    get<PageResult<OrderItem>>('/order/page', params),
  detail: (orderNo: string) => get<OrderItem>(`/order/${orderNo}`),
  cancel: (orderNo: string, reason?: string) => put<void>(`/order/${orderNo}/cancel`, { reason }),
  ship: (orderNo: string, logisticsNo?: string) =>
    put<void>(`/order/${orderNo}/ship`, { logisticsNo }),
  receive: (orderNo: string) => put<void>(`/order/${orderNo}/receive`),
  review: (orderNo: string, data: { rating: number; content?: string; images?: string[]; anonymous?: boolean }) =>
    post<{ reviewId: string; direction: string }>(`/order/${orderNo}/review`, data),
  reviewMine: () => get<PageResult<any>>('/order/review/mine'),
  refundApply: (data: { orderNo: string; amount: number; reason: string }) =>
    post<void>('/order/refund', data),
  refundPage: (params: { pageNum?: number; pageSize?: number }) =>
    get<PageResult<any>>('/order/refund/page', params),
  dict: () => get<Record<string, any>>('/order/dict')
}

/* ---------------- Y 支付 ---------------- */
export const paymentApi = {
  pay: (orderNo: string) => post<Record<string, any>>('/payment/pay', { orderNo }),
  detail: (payNo: string) => get<Record<string, any>>(`/payment/${payNo}`),
  dict: () => get<Record<string, any>>('/payment/dict')
}

/* ---------------- M 消息 ---------------- */
export const messageApi = {
  conversations: (params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    get<PageResult<ConversationItem>>('/message/conversations', params),
  history: (params: { conversationId: string; lastId?: string; size?: number }) =>
    get<{ records: ChatMessage[]; hasMore: boolean; lastId: string | null }>('/message/history', params),
  send: (data: { conversationId: string; toId: string; contentType: string; content: string; clientMsgId?: string }) =>
    post<ChatMessage>('/message/send', data),
  read: (data: { conversationId?: string; lastMsgId?: string; noticeIds?: string[] }) =>
    put<{ unreadTotal: number }>('/message/read', data),
  notices: (params: { pageNum?: number; pageSize?: number; type?: string; unreadOnly?: boolean }) =>
    get<PageResult<NoticeItem>>('/message/notices', params),
  noticeUnreadCount: () => get<{ total: number }>('/message/notices/unread-count'),
  createConversation: (data: { peerId: string; productId: string; openWithProductCard?: boolean }) =>
    post<{ conversationId: string; created: boolean }>('/message/conversation', data)
}

/* ---------------- 管理端 ---------------- */
export const adminUserApi = {
  page: (params: {
    keyword?: string
    role?: string
    status?: string
    canPublish?: number
    pageNum?: number
    pageSize?: number
  }) => get<PageResult<any>>('/user/admin/page', params),
  detail: (id: string) => get<any>(`/user/admin/${id}`),
  ban: (id: string, data: { banUntil?: string; permanent?: boolean; reason?: string }) =>
    put<void>(`/user/admin/${id}/ban`, data),
  unban: (id: string) => put<void>(`/user/admin/${id}/unban`),
  publishPermission: (id: string, data: { canPublish: boolean; publishBanUntil?: string; permanent?: boolean }) =>
    put<void>(`/user/admin/${id}/publish-permission`, data),
  setRole: (id: string, role: string) => put<void>(`/user/admin/${id}/role`, { role })
}

export const adminProductApi = {
  page: (params: any) => get<PageResult<any>>('/product/admin/page', params),
  offShelf: (id: string, reason?: string) => put<void>(`/product/admin/${id}/off-shelf`, { reason }),
  statistics: () => get<Record<string, number>>('/product/admin/statistics')
}

export const adminOrderApi = {
  page: (params: any) => get<PageResult<any>>('/order/admin/page', params),
  statistics: () => get<Record<string, number>>('/order/admin/statistics'),
  refundPage: (params: any) => get<PageResult<any>>('/order/admin/refund/page', params),
  handleRefund: (id: string, data: { agree: boolean; remark?: string }) =>
    put<void>(`/order/admin/refund/${id}/handle`, data)
}
