<template>
  <a-layout-header class="header">
    <a-row :wrap="false" align="middle">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.svg" alt="Logo" />
            <h1 class="site-title">Woopsion-ai</h1>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col>
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown>
              <a-space>
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                {{ loginUserStore.loginUser.userName ?? '无名' }}
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter, type RouteRecordRaw } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { LogoutOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import checkAccess from '@/access/checkAccess'
import ACCESS_ENUM from '@/access/accessEnum'

const loginUserStore = useLoginUserStore()
const router = useRouter()

// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])

// 监听路由变化，更新当前选中菜单
router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

// 用户注销
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    // 全局清除
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}

// 菜单配置项
const originItems = [
  {
    key: '/',
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: 'others',
    label: h('a', { href: 'https://github.com/Woopsion', target: '_blank' }, 'woopsion'),
    title: 'Woopsion',
  },
]

/**
 * 将菜单项转换为路由项（从 router 中查找对应的路由配置）
 * @param menu 菜单配置
 * @returns 路由记录或 null
 */
const menuToRouteItem = (menu: any): RouteRecordRaw | null => {
  const menuKey = menu.key as string
  
  // 如果不是路径（比如外部链接），返回 null
  if (!menuKey.startsWith('/')) {
    return null
  }
  
  // 从路由表中查找对应的路由
  const route = router.getRoutes().find(route => route.path === menuKey)
  
  return route || null
}

/**
 * 过滤菜单项（根据权限和隐藏设置）
 * @param menus 原始菜单数组
 * @returns 过滤后的菜单数组
 */
const filterMenus = (menus: typeof originItems) => {
  return menus.filter((menu) => {
    // 将菜单项转换为路由项
    const route = menuToRouteItem(menu)
    
    // 如果找不到对应的路由（比如外部链接），则默认显示
    if (!route) {
      return true
    }
    
    // 检查路由是否设置了 hideInMenu
    if (route.meta?.hideInMenu) {
      return false
    }
    
    // 获取路由要求的权限
    const needAccess = (route.meta?.access as string) || ACCESS_ENUM.NOT_LOGIN
    
    // 根据权限过滤菜单，有权限则返回 true，保留该菜单
    return checkAccess(loginUserStore.loginUser, needAccess)
  })
}

// 展示在菜单的路由数组（使用 computed 实现响应式更新）
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}
</script>

<style scoped>
.header {
  background: #fff;
  padding: 0 24px;
  height: 64px;
  line-height: 1;
}

.header :deep(.ant-row) {
  height: 100%;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 100%;
}

.logo {
  height: 40px;
  width: 40px;
}

.site-title {
  margin: 0;
  font-size: 18px;
  color: #1890ff;
  line-height: 1.2;
}

.header :deep(.ant-menu-horizontal) {
  border-bottom: none !important;
  line-height: 64px;
}

.user-login-status {
  display: flex;
  align-items: center;
}
</style>
