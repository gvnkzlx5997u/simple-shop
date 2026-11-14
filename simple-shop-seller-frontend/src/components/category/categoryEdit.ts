/** 行内编辑目标：全局同一时刻只有一个，由页面持有，卡片据此渲染编辑表单。 */
export type CategoryEditTarget =
  | { kind: 'rename'; level: 'l1' | 'l2'; id: string }
  | { kind: 'add'; l1Id: string }