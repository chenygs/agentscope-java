import { readFileSync } from 'fs';

// 模拟用户发送的 AI 输出（从 slides3.json 读取完整内容再模拟）
const text = `配图: 3张 Pixabay CC0 免版权图片（学生学习、文具/备考场景）[PPT_DATA]

\`\`\`slides.json
{
  "title": "2026高考英语作文全攻略",
  "template_id": "minimal-business-summary",
  "outputFile": "2026-Gaokao-English-Writing.pptx",
  "theme": {
    "colors": {
      "background": "FFFFFF",
      "surface": "E7E6E6",
      "primary": "485275",
      "secondary": "44546A",
      "accent": "5B7FA5",
      "text": "1A1A2E",
      "textSecondary": "44546A",
      "textMuted": "7A7A7A",
      "border": "D0D0D0",
      "success": "2E7D32",
      "warning": "ED6C02",
      "error": "D32F2F"
    },
    "fonts": {
      "title": { "family": "微软雅黑", "size": 36, "color": "1A1A2E" },
      "subtitle": { "family": "Arial", "size": 20, "color": "44546A" },
      "body": { "family": "微软雅黑", "size": 14, "color": "1A1A2E" },
      "caption": { "family": "Arial", "size": 12, "color": "7A7A7A" }
    }
  },
  "slides": [
    {
      "elements": [
        { "method": "bg", "options": { "fill": { "color": "FFFFFF" } } },
        { "method": "shape", "shapeType": "rect", "options": { "x": 0, "y": 0, "w": 10, "h": 0.08, "fill": { "color": "485275" } } },
        { "method": "shape", "shapeType": "rect", "options": { "x": 0.6, "y": 0.6, "w": 5.2, "h": 4.0, "fill": { "color": "FFFFFF" }, "rectRadius": 0.08 } },
        { "method": "text", "text": "Gaokao English Writing 2026", "options": { "x": 0.9, "y": 1.0, "w": 5.0, "h": 0.7, "fontSize": 20, "fontFace": "Arial", "bold": false, "color": "44546A", "align": "left", "italic": true } },
        { "method": "text", "text": "1", "options": { "x": 9.3, "y": 5.2, "w": 0.5, "h": 0.3, "fontSize": 10, "color": "7A7A7A", "align": "center" } }
      ]
    }
  ]
}
\`\`\``;

console.log('=== [PPT_DATA] 正则测试 ===');

// Strategy 0: [PPT_DATA]
const m0 = text.match(/\[PPT_DATA\]\s*```[\w.]*\s*\n?([\s\S]*?)```/);
if (m0) {
  try {
    const d = JSON.parse(m0[1].trim());
    if (d && Array.isArray(d.slides)) {
      console.log('Strategy 0 matched! slides count:', d.slides.length, 'title:', d.title);
    }
  } catch(e) {
    console.log('Strategy 0 parse error:', e.message);
    // 打印捕获的内容长度
    console.log('Captured length:', m0[1].trim().length);
    console.log('Captured first 100:', m0[1].trim().slice(0, 100));
    console.log('Captured last 100:', m0[1].trim().slice(-100));
  }
} else {
  console.log('Strategy 0 FAILED - no match');
}

// 再测试一个变体：[PPT_DATA] 紧跟在句子末尾没有空格
const text2 = `配图: 3张 Pixabay CC0 免版权图片（学生学习、文具/备考场景）[PPT_DATA]
\`\`\`slides.json
{
  "title": "测试2",
  "slides": []
}
\`\`\``;

const m0b = text2.match(/\[PPT_DATA\]\s*```[\w.]*\s*\n?([\s\S]*?)```/);
console.log('\n=== 变体测试 ([PPT_DATA]末尾无空格) ===');
console.log(m0b ? 'Strategy 0 matched! title: ' + JSON.parse(m0b[1].trim()).title : 'FAILED');
