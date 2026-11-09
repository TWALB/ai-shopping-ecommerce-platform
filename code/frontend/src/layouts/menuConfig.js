/**
 * 管理后台侧栏菜单配置。
 *
 * 之所以把菜单从模板里抽出来：
 * - 加一组/加一项只改数据，不用再复制十几行 el-sub-menu；
 * - 面包屑、选中高亮、展开态都能从同一份数据推导，不会出现「菜单改了面包屑没改」；
 * - 后续按角色做菜单裁剪时，只过滤这份数组即可。
 *
 * icon 用 Element Plus 图标组件名（main.js 已全局注册），children 为空则渲染为一级菜单项。
 */
export const adminMenu = [
  {
    key: 'ops',
    title: '平台运营',
    icon: 'DataAnalysis',
    children: [
      { path: '/admin?tab=stats', title: '数据总览', icon: 'Odometer' },
      { path: '/admin?tab=users', title: '用户管理', icon: 'User' },
      { path: '/admin?tab=applies', title: '入驻审核', icon: 'Shop' },
      { path: '/admin?tab=products', title: '商品管理', icon: 'Goods' },
      { path: '/admin?tab=orders', title: '订单管理', icon: 'Tickets' },
      { path: '/admin?tab=aftersales', title: '售后管理', icon: 'Service' },
      { path: '/admin?tab=notices', title: '公告管理', icon: 'Bell' },
      { path: '/admin?tab=comments', title: '评价审核', icon: 'ChatLineSquare' },
      { path: '/admin?tab=logs', title: '操作日志', icon: 'Document' }
    ]
  },
  {
    key: 'kb',
    title: 'AI 知识库（RAG）',
    icon: 'Reading',
    children: [
      { path: '/admin/ai/knowledge/products', title: '商品知识库', icon: 'Collection' },
      { path: '/admin/ai/knowledge/chunks', title: '商品知识切片', icon: 'Files' },
      { path: '/admin/ai/knowledge/search', title: 'Embedding 检索', icon: 'Search' }
    ]
  },
  {
    key: 'guide',
    title: 'AI 智能导购与运营',
    icon: 'MagicStick',
    children: [
      { path: '/admin/ai/guide/tasks', title: 'AI 智能导购任务', icon: 'ChatDotRound' },
      { path: '/admin/ai/guide/runs', title: 'Agent Run 运行记录', icon: 'VideoPlay' },
      { path: '/admin/ai/guide/steps', title: 'Agent Step 执行步骤', icon: 'SetUp' },
      { path: '/admin/ai/guide/recommend', title: 'AI 推荐商品', icon: 'Star' },
      { path: '/admin/ai/qa', title: 'AI 商品问答', icon: 'ChatLineSquare' },
      { path: '/admin/ai/comment', title: 'AI 评价分析', icon: 'DataLine' },
      { path: '/admin/ai/report', title: 'AI 运营增长报告', icon: 'TrendCharts' }
    ]
  },
  {
    key: 'base',
    title: 'AI 基础配置',
    icon: 'Setting',
    children: [
      { path: '/admin/ai/model', title: 'AI 模型配置', icon: 'Cpu' },
      { path: '/admin/ai/prompt', title: 'Prompt 模板', icon: 'Document' },
      { path: '/admin/ai/tool', title: 'Function Tool', icon: 'Connection' },
      { path: '/admin/ai/tool/debug/product', title: '商品工具调试', icon: 'Monitor' },
      { path: '/admin/ai/tool/debug/biz', title: '业务工具调试', icon: 'Monitor' }
    ]
  }
]

/** 默认展开的组：AI 相关三组，平台运营收起（避免侧栏一屏放不下） */
export const DEFAULT_OPENED = ['kb', 'guide', 'base']

/** 扁平化，便于按路径反查标题 */
const FLAT = adminMenu.flatMap((group) =>
  group.children.map((item) => ({ ...item, groupKey: group.key, groupTitle: group.title }))
)

/**
 * 当前路由对应的菜单路径。
 * 平台运营走 /admin?tab=xxx 的 query 形式，需要单独归一化，否则 el-menu 高亮不上。
 */
export function resolveMenuPath(path, query = {}) {
  if (path && path.startsWith('/admin/ai')) {
    return path
  }
  return `/admin?tab=${query.tab || 'stats'}`
}

/** 面包屑层级：管理后台 / 组名 / 页面名 */
export function resolveChain(menuPath) {
  const hit = FLAT.find((item) => item.path === menuPath)
  if (!hit) {
    return ['管理后台']
  }
  return ['管理后台', hit.groupTitle, hit.title]
}
