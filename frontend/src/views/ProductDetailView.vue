<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { messageApi, productApi } from '@/api'
import { useUserStore } from '@/stores/user'
import SellerCardComp from '@/components/SellerCard.vue'
import StarRating from '@/components/StarRating.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import { conditionLabel } from '@/utils/product'
import type { CommentItem, ProductDetail } from '@/types'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const detail = ref<ProductDetail | null>(null)
const comments = ref<CommentItem[]>([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentFinished = ref(false)
const activeImage = ref(0)
const loading = ref(true)
const commentText = ref('')
const commentImages = ref<string[]>([])
const submitting = ref(false)
const replyTo = ref<CommentItem | null>(null)

const id = computed(() => String(route.params.id || ''))
const sellerId = computed(() => detail.value?.sellerId || detail.value?.seller?.id || '')
const isOwner = computed(() => !!detail.value && sellerId.value === user.profile?.id)
const images = computed(() => {
  const list = detail.value?.images?.length ? [...detail.value.images] : []
  if (!list.length && detail.value?.coverUrl) list.push(detail.value.coverUrl)
  return list
})

async function load() {
  loading.value = true
  try {
    detail.value = await productApi.detail(id.value)
    await loadComments(true)
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function loadComments(reset = false) {
  if (reset) {
    commentPage.value = 1
    commentFinished.value = false
  }
  const page = await productApi.comments(id.value, { pageNum: commentPage.value, pageSize: 10 })
  comments.value = reset ? page.records : [...comments.value, ...page.records]
  commentTotal.value = Number(page.total)
  commentFinished.value = comments.value.length >= Number(page.total)
}

function moreComments() {
  if (commentFinished.value) return
  commentPage.value += 1
  loadComments()
}

async function toggleFavorite() {
  if (!detail.value) return
  if (!user.isLogin) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (detail.value.favorited) {
    await productApi.unfavorite(id.value)
    detail.value.favorited = false
    detail.value.favoriteCount = Math.max(0, (detail.value.favoriteCount || 0) - 1)
  } else {
    await productApi.favorite(id.value)
    detail.value.favorited = true
    detail.value.favoriteCount = (detail.value.favoriteCount || 0) + 1
  }
}

function buy() {
  if (!detail.value) return
  if (!user.isLogin) {
    router.push({ path: '/login', query: { redirect: `/purchase/${id.value}` } })
    return
  }
  router.push(`/purchase/${id.value}`)
}

async function contact() {
  if (!detail.value) return
  try {
    const r = await messageApi.createConversation({
      peerId: detail.value.sellerId as string,
      productId: id.value,
      openWithProductCard: true
    })
    router.push(`/chat?cid=${r.conversationId}`)
  } catch {
    /* 拦截器提示 */
  }
}

async function offShelf() {
  await ElMessageBox.confirm('下架后买家将无法看到该商品，确定下架？', '下架商品', { type: 'warning' })
  await productApi.offShelf(id.value, '卖家自行下架')
  ElMessage.success('已下架')
  load()
}

async function onShelf() {
  await productApi.onShelf(id.value)
  ElMessage.success('已重新上架')
  load()
}

async function submitComment() {
  if (!commentText.value.trim()) {
    ElMessage.warning('说点什么吧')
    return
  }
  submitting.value = true
  try {
    await productApi.addComment(id.value, {
      content: commentText.value.trim(),
      images: commentImages.value,
      parentId: replyTo.value?.id
    })
    ElMessage.success('评论成功')
    commentText.value = ''
    commentImages.value = []
    replyTo.value = null
    await loadComments(true)
    if (detail.value) detail.value.commentCount = (detail.value.commentCount || 0) + 1
  } finally {
    submitting.value = false
  }
}

async function removeComment(c: CommentItem) {
  await ElMessageBox.confirm('确定删除这条评论？', '删除评论', { type: 'warning' })
  await productApi.deleteComment(c.id)
  ElMessage.success('已删除')
  await loadComments(true)
}

function toSeller() {
  if (sellerId.value) router.push(`/seller/${sellerId.value}`)
}

onMounted(load)
</script>

<template>
  <div class="detail mall-page" v-loading="loading">
    <template v-if="detail">
      <el-alert
        v-if="detail.status === 'OFF_SHELF'"
        type="warning"
        :closable="false"
        show-icon
        class="status-tip"
        :title="`该商品已下架${detail.offShelfReason ? '：' + detail.offShelfReason : ''}`"
      />

      <div class="top mall-card">
        <div class="gallery">
          <div class="thumbs">
            <img
              v-for="(img, i) in images"
              :key="img"
              :src="img"
              :class="{ active: activeImage === i }"
              @mouseenter="activeImage = i"
              alt=""
            />
          </div>
          <div class="main-img">
            <img v-if="images[activeImage]" :src="images[activeImage]" :alt="detail.title" />
            <div v-else class="no-img">暂无图片</div>
          </div>
        </div>

        <div class="info">
          <h1 class="title">{{ detail.title }}</h1>
          <div class="price-row">
            <span class="mall-price price">¥{{ detail.price }}</span>
            <span v-if="detail.originalPrice" class="origin">原价 ¥{{ detail.originalPrice }}</span>
            <span class="cond">{{ conditionLabel(detail.conditionLevel) || '成色未填' }}</span>
          </div>
          <div class="chips">
            <span class="chip">{{ detail.freightHint || '运费见下单页' }}</span>
            <span class="chip">{{ detail.city || '地区未填' }}</span>
            <span v-if="detail.categoryName" class="chip">{{ detail.categoryName }}</span>
          </div>
          <p class="desc">{{ detail.description || '卖家没有填写描述' }}</p>
          <div class="stat">
            <span>浏览 {{ detail.viewCount || 0 }}</span>
            <span>收藏 {{ detail.favoriteCount || 0 }}</span>
            <span>评论 {{ detail.commentCount || 0 }}</span>
          </div>

          <div class="actions">
            <el-button :type="detail.favorited ? 'warning' : 'default'" plain @click="toggleFavorite">
              {{ detail.favorited ? '★ 已收藏' : '☆ 收藏' }}
            </el-button>
            <template v-if="!isOwner">
              <el-button type="primary" size="large" :disabled="detail.status !== 'ON_SALE'" @click="buy">
                {{ detail.status === 'SOLD' ? '已售出' : detail.status === 'LOCKED' ? '交易中' : '立即购买' }}
              </el-button>
              <el-button size="large" plain @click="contact">联系卖家</el-button>
            </template>
            <template v-else>
              <el-button @click="router.push(`/publish/${detail.id}`)">编辑</el-button>
              <el-button v-if="detail.status === 'ON_SALE'" @click="offShelf">下架</el-button>
              <el-button v-if="detail.status === 'OFF_SHELF'" type="primary" @click="onShelf">重新上架</el-button>
            </template>
          </div>
        </div>
      </div>

      <SellerCardComp v-if="detail.seller" :seller="detail.seller" class="seller" @open="toSeller" />

      <div class="comments mall-card">
        <div class="comments-head">
          <h3>商品评论 <span class="mall-sub">({{ detail.commentCount || 0 }})</span></h3>
        </div>

        <div class="comment-editor">
          <div v-if="replyTo" class="replying">
            回复 @{{ replyTo.user?.nickname }}
            <el-button link size="small" @click="replyTo = null">取消</el-button>
          </div>
          <el-input
            v-model="commentText"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="user.isLogin ? '问问卖家细节，或者聊聊成色…' : '登录后可以评论'"
            :disabled="!user.isLogin"
          />
          <div class="editor-foot">
            <ImageUploader v-model="commentImages" :limit="3" biz-type="review" />
            <el-button type="primary" :loading="submitting" :disabled="!user.isLogin" @click="submitComment">
              发表评论
            </el-button>
          </div>
        </div>

        <div v-for="c in comments" :key="c.id" class="comment">
          <el-avatar :size="34" :src="c.user?.avatar || undefined">
            {{ (c.user?.nickname || '?').slice(0, 1) }}
          </el-avatar>
          <div class="comment-body">
            <div class="comment-top">
              <span class="uname">{{ c.user?.nickname || '匿名用户' }}</span>
              <span class="time">{{ c.createTime }}</span>
            </div>
            <p class="content">{{ c.content }}</p>
            <div v-if="c.images?.length" class="cimgs">
              <el-image v-for="img in c.images" :key="img" :src="img" :preview-src-list="c.images" fit="cover" />
            </div>
            <div class="ops">
              <el-button link size="small" @click="replyTo = c">回复</el-button>
              <el-button
                v-if="c.user?.id === user.profile?.id || user.isAdmin"
                link
                size="small"
                @click="removeComment(c)"
              >
                删除
              </el-button>
            </div>
            <div v-for="r in c.replies || []" :key="r.id" class="reply">
              <b>{{ r.user?.nickname || '匿名用户' }}</b>：{{ r.content }}
              <span class="time">{{ r.createTime }}</span>
            </div>
          </div>
        </div>

        <div class="more">
          <el-button v-if="!commentFinished && comments.length" link @click="moreComments">加载更多评论</el-button>
          <span v-else-if="!comments.length" class="mall-empty">还没有评论，来说第一句吧</span>
        </div>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="商品不存在或已被删除" />
  </div>
</template>

<style scoped>
.detail {
  padding: 24px;
}
.status-tip {
  margin-bottom: 16px;
}
.top {
  display: flex;
  gap: 28px;
  padding: 20px;
}
.gallery {
  display: flex;
  gap: 12px;
  width: 520px;
  flex: 0 0 520px;
}
.thumbs {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.thumbs img {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 6px;
  border: 2px solid transparent;
  cursor: pointer;
}
.thumbs img.active {
  border-color: var(--mall-primary);
}
.main-img {
  flex: 1;
  background: #f5f6f8;
  border-radius: 8px;
  overflow: hidden;
}
.main-img img {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: contain;
  display: block;
}
.no-img {
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mall-text-3);
}
.info {
  flex: 1;
  min-width: 0;
}
.title {
  font-size: 22px;
  margin: 0 0 12px;
  line-height: 1.4;
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.price {
  font-size: 30px;
}
.origin {
  font-size: 13px;
  color: var(--mall-text-3);
  text-decoration: line-through;
}
.cond {
  font-size: 12px;
  background: #f2f3f5;
  padding: 2px 8px;
  border-radius: 10px;
  color: var(--mall-text-2);
}
.chips {
  margin: 12px 0;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.chip {
  font-size: 12px;
  color: var(--mall-text-2);
  background: #f7f8fa;
  border-radius: 4px;
  padding: 3px 8px;
}
.desc {
  font-size: 14px;
  line-height: 1.7;
  color: var(--mall-text-2);
  white-space: pre-wrap;
  max-height: 160px;
  overflow: auto;
}
.stat {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--mall-text-3);
  margin: 10px 0 18px;
}
.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.seller {
  margin-top: 16px;
}
.comments {
  margin-top: 16px;
  padding: 20px;
}
.comments-head h3 {
  margin: 0 0 16px;
  font-size: 16px;
}
.comment-editor {
  background: #fafbfc;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 20px;
}
.replying {
  font-size: 12px;
  color: var(--mall-primary);
  margin-bottom: 6px;
}
.editor-foot {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
}
.comment {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid var(--mall-border);
}
.comment-body {
  flex: 1;
  min-width: 0;
}
.comment-top {
  display: flex;
  gap: 10px;
  align-items: baseline;
}
.uname {
  font-weight: 600;
  font-size: 14px;
}
.time {
  font-size: 12px;
  color: var(--mall-text-3);
}
.content {
  margin: 6px 0;
  font-size: 14px;
  line-height: 1.6;
}
.cimgs {
  display: flex;
  gap: 8px;
}
.cimgs :deep(img) {
  width: 80px;
  height: 80px;
  border-radius: 6px;
}
.ops {
  margin-top: 4px;
}
.reply {
  margin-top: 8px;
  background: #f7f8fa;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 13px;
  color: var(--mall-text-2);
}
.more {
  text-align: center;
  padding-top: 12px;
}
</style>
