const fs = require('fs');
const content = fs.readFileSync('D:/wuziqi/src/main/resources/static/login.html', 'utf8');

// 提取script内容
const scriptMatch = content.match(/<script>([\s\S]*?)<\/script>/);
if (scriptMatch) {
    const jsCode = scriptMatch[1];

    try {
        // 尝试解析语法
        new Function(jsCode);
        console.log('✓ JavaScript语法正确');
    } catch (e) {
        console.error('✗ JavaScript语法错误:');
        console.error('错误信息:', e.message);
        console.error('错误位置:', e.stack);

        // 找到错误附近的代码
        const lines = jsCode.split('\n');
        if (e.stack) {
            const match = e.stack.match(/<anonymous>:(\d+):(\d+)/);
            if (match) {
                const lineNum = parseInt(match[1]) - 1;
                console.error('\n错误附近的代码:');
                for (let i = Math.max(0, lineNum - 5); i < Math.min(lines.length, lineNum + 5); i++) {
                    const prefix = (i === lineNum - 1) ? '>>> ' : '    ';
                    console.log(`${prefix}${lines[i]}`);
                }
            }
        }
    }
} else {
    console.log('✗ 未找到script标签');
}
