/**
 * slides.json -> PPTX Blob 渲染器
 *
 * 从 ppt-agent 旧版前端 index.html 提取的 PptxGenJS 渲染逻辑。
 * 输入: AI 生成的 slides.json
 * 输出: 浏览器下载的 .pptx 文件 Blob
 */

import PptxGenJS from 'pptxgenjs';

interface ThemeColors {
  [key: string]: string;
}

interface SlideData {
  title?: string;
  outputFile?: string;
  template_id?: string;
  theme?: {
    colors?: ThemeColors;
    fonts?: any;
  };
  slides: Array<{ elements?: any[] }>;
}

const DEFAULT_COLORS: ThemeColors = {
  background: '0f172a',
  surface: '1e293b',
  primary: '3b82f6',
  secondary: '06b6d4',
  accent: 'f59e0b',
  text: 'f8fafc',
  textSecondary: '94a3b8',
  textMuted: '64748b',
  border: '334155',
};

/**
 * 解析消息文本中的 slides.json 代码块
 *
 * 只匹配 ```slides.json 代码块，提取其中的 JSON 数据。
 * 解析失败时尝试简单修复中文引号问题。
 */
export function parseSlidesJson(text: string): SlideData | null {
  if (!text) return null;
  const match = text.match(/```slides\.json\s*\n?([\s\S]*?)```/);
  if (!match) return null;
  try {
    const jsonText = match[1]?.trim();
    if (!jsonText) return null;
    const data = tryParseJson(jsonText);
    if (!data || !Array.isArray(data.slides)) return null;
    return ensureTheme(data);
  } catch (e) {
    console.warn('Failed to parse slides.json:', e);
    return null;
  }
}

/**
 * 检测文本中是否包含 ```slides.json 代码块
 */
export function hasSlidesJson(text: string): boolean {
  return /```slides\.json/.test(text);
}

/**
 * 尝试解析 JSON，失败时修复中文引号问题
 * - 中文左引号 " → 替换为「
 * - 中文右引号 " → 替换为」
 */
function tryParseJson(text: string): any {
  try {
    return JSON.parse(text);
  } catch {
    // fall through
  }
  const repaired = text
    .replace(/[“”]/g, '「')
    .replace(/[‘’]/g, "'");
  try {
    return JSON.parse(repaired);
  } catch {
    return null;
  }
}

/** 补全缺失的 theme.colors */
function ensureTheme(data: SlideData): SlideData {
  if (!data.theme) data.theme = {};
  if (!data.theme.colors) data.theme.colors = {};
  const colors = data.theme.colors;
  for (const [k, v] of Object.entries(DEFAULT_COLORS)) {
    if (!colors[k]) colors[k] = v;
  }
  return data;
}

/** 主入口：把 slides.json 渲染成 PPTX Blob */
export async function renderPptx(slideData: SlideData): Promise<Blob> {
  const data = ensureTheme(slideData);
  const pres = new PptxGenJS();
  pres.defineLayout({ name: 'WIDE', width: 10, height: 5.625 });
  pres.layout = 'WIDE';

  const c = data.theme!.colors!;
  const totalSlides = data.slides.length;

  // 解析主题色键名为实际 hex
  function tc(color: any): any {
    if (!color || typeof color !== 'string') return color;
    return c[color] || color;
  }

  // 递归处理 options 内的颜色键
  function applyTheme(opts: any): any {
    if (!opts || typeof opts !== 'object') return opts;
    const o = JSON.parse(JSON.stringify(opts));
    delete o.letterSpacing;
    delete o.headerFill;
    delete o.fillColor;
    delete o.objectName;
    if (o.bullet && typeof o.bullet === 'object' && o.bullet.type === 'bullet') {
      o.bullet = true;
    }
    const fs = (v: any) => (v != null && typeof v === 'string' ? v : null);
    if (o.color) o.color = tc(fs(o.color)) || o.color;
    if (o.valueColor) o.valueColor = tc(fs(o.valueColor)) || o.valueColor;
    if (o.fill && typeof o.fill === 'object' && o.fill.color) {
      o.fill.color = tc(fs(o.fill.color)) || o.fill.color;
    }
    if (o.line && o.line.color) o.line.color = tc(fs(o.line.color)) || o.line.color;
    if (o.shadow && o.shadow.color)
      o.shadow.color = tc(fs(o.shadow.color)) || o.shadow.color;
    if (o.border && o.border.color)
      o.border.color = tc(fs(o.border.color)) || o.border.color;
    if (o.chartColors && Array.isArray(o.chartColors)) {
      o.chartColors = o.chartColors.map((x: any) => tc(fs(x)) || x);
    }
    return o;
  }
  // 递归解析表格单元格里的主题色
  function resolveTableCells(rows: any[]): any[] {
    if (!rows?.length) return rows;
    return rows.map((row) =>
      !row?.length
        ? row
        : row.map((cell: any) =>
            cell && cell.options
              ? { text: cell.text, options: applyTheme(cell.options) }
              : cell,
          ),
    );
  }

  function execElement(sl: any, el: any): void {
    if (!el?.method) return;
    try {
      switch (el.method) {
        case 'bg': {
          if (el.options?.gradient?.colorStops) {
            const gs = el.options.gradient.colorStops.map((g: any) => ({
              color: tc(g.color),
            }));
            sl.background = {
              gradient: {
                type: el.options.gradient.type || 'linear',
                colorStops: gs,
              },
            };
          } else if (el.options?.fill?.color) {
            sl.background = { color: tc(el.options.fill.color) };
          } else if (el.options?.color) {
            sl.background = { color: tc(el.options.color) };
          }
          break;
        }
        case 'chart': {
          sl.addChart(
            (pres as any).ChartType[el.chartType || 'bar'],
            el.chartData || [],
            applyTheme(el.options || {}),
          );
          break;
        }
        case 'image': {
          sl.addImage(applyTheme(el.options || {}));
          break;
        }
        case 'shape': {
          sl.addShape(
            (pres as any).ShapeType[el.shapeType || 'rect'],
            applyTheme(el.options || {}),
          );
          break;
        }
        case 'table': {
          sl.addTable(
            resolveTableCells(el.rows || []),
            applyTheme(el.options || {}),
          );
          break;
        }
        case 'text': {
          sl.addText(el.text || '', applyTheme(el.options || {}));
          break;
        }
      }
    } catch (ex) {
      console.warn('Element failed:', el.method, ex);
    }
  }

  data.slides.forEach((slide, idx) => {
    const sl = pres.addSlide();
    (slide.elements || []).forEach((el) => execElement(sl, el));
    sl.addText(`${idx + 1}/${totalSlides}`, {
      x: 9.3,
      y: 5.1,
      w: 0.6,
      h: 0.3,
      fontSize: 8,
      color: c.textMuted || '64748b',
      align: 'right',
      fontFace: 'Arial',
      transparency: 50,
    });
  });

  return (await pres.write({ outputType: 'blob' })) as Blob;
}

/** 触发浏览器下载 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}
