/** 与后端 R<T> / PageResult<T> 对应；Long 与 BigDecimal 都被序列化成字符串，这里统一写 string。 */
export interface R<T> {
  code: number
  message: string
  data: T
  timestamp?: string
}

export interface PageResult<T> {
  records: T[]
  total: number | string
  pageNum: number | string
  pageSize: number | string
  pages: number | string
  guestLimited?: boolean
}

export interface UserInfo {
  id: string
  username: string
  nickname: string
  avatar: string
  email?: string
  phone?: string | null
  city?: string | null
  signature?: string | null
  role: 'USER' | 'ADMIN' | 'SUPER_ADMIN'
  status?: string
  canPublish?: boolean
  publishBanUntil?: string | null
  creditScore?: number | null
  lastLoginTime?: string | null
  province?: string | null
  district?: string | null
  gender?: number | null
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number | string
  userInfo: UserInfo
}

export interface CategoryNode {
  id: string
  name: string
  icon?: string
  children?: CategoryNode[]
}

export interface SellerCard {
  id: string
  nickname: string
  avatar: string
  city?: string | null
  lastLoginTime?: string | null
  registerDays?: number
  soldCount?: number
  onSaleCount?: number
  avgRating?: number | string | null
  reviewCount?: number
  creditScore?: number
}

export interface ProductCard {
  id: string
  title: string
  coverUrl?: string
  price: string
  originalPrice?: string | null
  conditionLevel?: number
  city?: string
  freightMode?: string
  status?: string
  sellerId?: string
  sellerNickname?: string
  sellerAvatar?: string
  favoriteCount?: number
  commentCount?: number
  viewCount?: number
  createTime?: string
}

export interface ProductDetail extends ProductCard {
  description?: string
  categoryId?: string
  categoryName?: string
  province?: string
  district?: string
  images?: string[]
  freightHint?: string
  favorited?: boolean
  offShelfReason?: string | null
  seller?: SellerCard
}

export interface CommentItem {
  id: string
  parentId?: string
  content: string
  images?: string[]
  createTime: string
  replyCount?: number
  user?: { id: string; nickname: string; avatar: string }
  replies?: CommentItem[]
}

export interface AddressItem {
  id: string
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault?: boolean | number
}

export interface OrderItem {
  orderNo: string
  status: string
  totalAmount: string
  freight: string
  payAmount: string
  sameCity?: boolean
  freightMode?: string
  productId: string
  productTitle?: string
  productCover?: string
  counterpartId?: string
  counterpartNickname?: string
  buyerId?: string
  sellerId?: string
  logisticsNo?: string | null
  remark?: string | null
  createTime?: string
  payTime?: string | null
  shipTime?: string | null
  receiveTime?: string | null
  finishTime?: string | null
  cancelTime?: string | null
  cancelReason?: string | null
  expireTime?: string | null
  buyerReviewed?: boolean
  sellerReviewed?: boolean
}

export interface ConversationItem {
  conversationId: string
  peerId: string
  peerNickname: string
  peerAvatar?: string
  peerOnline?: boolean
  productId: string
  productCover?: string
  lastMsg: string
  lastMsgType: string
  lastMsgCardType?: string | null
  lastTime: string
  unreadCount: number
}

export interface ChatMessage {
  msgId: string
  conversationId: string
  fromId: string
  toId: string
  contentType: 'TEXT' | 'IMAGE' | 'PRODUCT' | 'ORDER'
  cardType?: string | null
  content: any
  status: string
  clientMsgId?: string
  createTime: string
}

export interface NoticeItem {
  noticeId: string
  type: string
  title: string
  content: string
  bizId?: string | null
  bizType?: string
  isRead: boolean
  createTime: string
}
