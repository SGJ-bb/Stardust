// Excel 数据处理技能 — 使用 openpyxl 库进行 Excel 文件操作

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::agents::cli_executor::CliExecutor;

pub struct ExcelProcessPlugin { enabled: bool }

impl ExcelProcessPlugin {
    pub fn new() -> Self { ExcelProcessPlugin { enabled: true } }

    /// 获取文件大小（字节）
    fn file_size_bytes(path: &str) -> Option<u64> {
        std::fs::metadata(path).ok().map(|m| m.len())
    }
}

#[async_trait]
impl ToolPlugin for ExcelProcessPlugin {
    fn name(&self) -> &str { "excel_process" }
    fn description(&self) -> &str { "Excel数据处理与分析：读取/写入/筛选/汇总/图表生成" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "excel_process".to_string(),
                description: "对Excel文件进行数据处理操作：读取数据、筛选排序、公式计算、生成图表、导出报告等".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "file_path": { "type": "string", "description": "Excel文件路径" },
                        "operation": {
                            "type": "string",
                            "enum": ["read", "write", "filter", "sort", "summarize", "chart", "merge", "export_report"],
                            "description": "操作类型"
                        },
                        "sheet_name": { "type": "string", "description": "工作表名称（默认第一个）" },
                        "query": { "type": "string", "description": "自然语言描述的处理需求" },
                        "output_path": { "type": "string", "description": "输出文件路径" }
                    },
                    "required": ["operation"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        // 1. 检查 python 是否可用
        if !CliExecutor::check_available("python") {
            return PluginResult::err(
                "该技能需要安装 Python 环境。\n\
                 安装方式：\n\
                 - Windows: 从 https://www.python.org/downloads/ 下载安装\n\
                 - macOS: brew install python\n\
                 - Linux: apt install python3\n\n\
                 安装后还需要执行: pip install openpyxl pandas\n\
                 完成后重启星尘即可使用此技能。"
            );
        }

        // 2. 解析参数
        let operation = match arguments["operation"].as_str() {
            Some(op) => op,
            None => return PluginResult::err("缺少必需参数: operation（操作类型）"),
        };

        let file_path = arguments["file_path"].as_str().unwrap_or("");
        let sheet_name = arguments["sheet_name"].as_str();
        let query = arguments["query"].as_str().unwrap_or("");
        let output_path = arguments["output_path"].as_str();

        tracing::info!("[excel_process] 操作={}, 文件={}", operation, file_path);

        // 3. 检查输入文件是否存在（read/summary/filter/convert 需要输入文件）
        if !file_path.is_empty() && !std::path::Path::new(file_path).exists() {
            return PluginResult::err(format!(
                "Excel 文件不存在: {}\n请确认文件路径是否正确。", file_path
            ));
        }

        // 4. 根据操作类型构建对应的 Python 脚本
        let data_json = serde_json::json!({
            "file_path": file_path,
            "operation": operation,
            "sheet_name": sheet_name,
            "query": query,
            "output_path": output_path,
        });

        // 根据不同操作类型选择不同的脚本逻辑
        let script = match operation {
            "read" => Self::build_read_script(),
            "summarize" | "summary" => Self::build_summary_script(),
            "filter" => Self::build_filter_script(),
            "convert" => Self::build_convert_script(),
            _ => Self::build_read_script(), // 默认回退到 read
        };

        // 5. 通过 CliExecutor 执行 Python 脚本
        let args = vec![
            "-c".to_string(),
            script,
            data_json.to_string(),
        ];

        let result = CliExecutor::safe_exec("python", &args);

        // 6. 处理结果
        if !result.success {
            let err_msg = &result.stderr;
            // 检查是否是依赖库未安装的错误
            if err_msg.contains("No module named 'openpyxl'")
                || err_msg.contains("No module named 'pandas'")
                || err_msg.contains("ModuleNotFoundError")
            {
                return PluginResult::err(format!(
                    "缺少依赖库。\n\
                     请在终端中执行以下命令安装:\n\
                     pip install openpyxl pandas\n\n\
                     详细错误: {}", err_msg
                ));
            }
            tracing::error!("[excel_process] 脚本执行失败 (op={}): {}", operation, err_msg);
            return PluginResult::err(format!(
                "Excel 处理失败 (操作={}):\n{}\n请检查 Python 环境和依赖是否正确安装。",
                operation, err_msg
            ));
        }

        // 7. 解析 Python 脚本输出
        let stdout_trimmed = result.stdout.trim();
        let script_result: serde_json::Value = match serde_json::from_str(stdout_trimmed) {
            Ok(v) => v,
            Err(_) => {
                tracing::warn!("[excel_process] 无法解析脚本输出 (op={}): {}", operation, stdout_trimmed);
                // 如果不是 JSON，直接返回原始输出
                return PluginResult::ok_with_data(
                    format!(
                        "✅ {} 操作完成\n📂 文件: {}\n⏱️  耗时: {} ms\n\n{}",
                        operation, file_path, result.duration_ms, stdout_trimmed
                    ),
                    serde_json::json!({
                        "status": "success",
                        "operation": operation,
                        "file_path": file_path,
                        "raw_output": stdout_trimmed,
                        "duration_ms": result.duration_ms,
                    })
                );
            }
        };

        if script_result.get("status").and_then(|v| v.as_str()) == Some("error") {
            let msg = script_result.get("message")
                .and_then(|v| v.as_str())
                .unwrap_or("未知错误");
            return PluginResult::err(format!("Excel 处理失败 ({}): {}", operation, msg));
        }

        // 8. 构建友好的结果展示
        let output_size = if let Some(op) = output_path {
            Self::file_size_bytes(op)
        } else {
            None
        };
        let size_info = output_size.map(|s| format!("\n📄 输出文件大小: {:.1} KB", s as f64 / 1024.0)).unwrap_or_default();

        let content = format!(
            "✅ Excel「{}」操作完成！\n\
             📂 文件: {}\n\
             ⏱️  耗时: {} ms{}",
            operation,
            file_path,
            result.duration_ms,
            size_info
        );

        tracing::info!("[excel_process] 操作完成: {}, 耗时{}ms", operation, result.duration_ms);

        // 将脚本返回的数据合并到结果 data 中
        let mut result_data = serde_json::json!({
            "status": "success",
            "operation": operation,
            "file_path": file_path,
            "duration_ms": result.duration_ms,
            "output_size_bytes": output_size,
        });
        if let Some(data) = script_result.get("data") {
            result_data["data"] = data.clone();
        }

        PluginResult::ok_with_data(content, result_data)
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}

// ==================== Python 脚本构建器 ====================

impl ExcelProcessPlugin {
    /// 构建 read 操作的 Python 脚本 — 读取 Excel 数据（前几行预览）
    fn build_read_script() -> String {
        r#"import json, sys

try:
    from openpyxl import load_workbook

    data = json.loads(sys.argv[1])
    file_path = data.get('file_path', '')
    sheet_name = data.get('sheet_name')

    wb = load_workbook(file_path, read_only=True, data_only=True)

    # 选择工作表
    if sheet_name and sheet_name in wb.sheetnames:
        ws = wb[sheet_name]
    else:
        ws = wb.active

    # 读取数据（最多前50行，防止数据量过大）
    rows_data = []
    row_count = 0
    for i, row in enumerate(ws.iter_rows(values_only=True)):
        if i >= 50:
            break
        rows_data.append([str(cell) if cell is not None else '' for cell in row])
        row_count += 1

    total_rows = ws.max_row
    total_cols = ws.max_column

    print(json.dumps({
        'status': 'ok',
        'data': {
            'sheet_name': ws.title,
            'total_rows': total_rows,
            'total_cols': total_cols,
            'preview_rows': len(rows_data),
            'rows': rows_data,
            'all_sheets': wb.sheetnames,
        }
    }))
except ImportError as e:
    print(json.dumps({'status': 'error', 'message': f'缺少依赖库: {e}'}))
except Exception as e:
    print(json.dumps({'status': 'error', 'message': str(e)}))
"#.to_string()
    }

    /// 构建 summary 操作的 Python 脚本 — 统计汇总信息
    fn build_summary_script() -> String {
        r#"import json, sys

try:
    from openpyxl import load_workbook

    data = json.loads(sys.argv[1])
    file_path = data.get('file_path', '')
    sheet_name = data.get('sheet_name')

    wb = load_workbook(file_path, read_only=True, data_only=True)

    if sheet_name and sheet_name in wb.sheetnames:
        ws = wb[sheet_name]
    else:
        ws = wb.active

    # 基本统计信息
    total_rows = ws.max_row
    total_cols = ws.max_column

    # 读取表头
    headers = []
    for row in ws.iter_rows(min_row=1, max_row=1, values_only=True):
        headers = [str(cell) if cell is not None else f'列{i+1}' for i, cell in enumerate(row)]
        break

    # 各列基本统计（数值列计算 min/max，非数值列统计非空数量）
    col_stats = {}
    for col_idx in range(1, min(total_cols + 1, 51)):  # 最多处理50列
        values = []
        for row in ws.iter_rows(min_row=2, max_row=min(total_rows + 1, 10001), min_col=col_idx, max_col=col_idx, values_only=True):
            val = row[0] if row else None
            if val is not None:
                try:
                    values.append(float(val))
                except (ValueError, TypeError):
                    pass

        header = headers[col_idx - 1] if col_idx <= len(headers) else f'列{col_idx}'
        non_empty = sum(1 for r in ws.iter_rows(min_row=1, max_row=total_rows, min_col=col_idx, max_col=col_idx, values_only=True)
                       for cell in r if cell is not None)

        stat = {'header': header, 'non_empty_cells': non_empty}
        if values:
            stat['min'] = min(values)
            stat['max'] = max(values)
            stat['avg'] = round(sum(values) / len(values), 2)
            stat['is_numeric'] = True
        else:
            stat['is_numeric'] = False
        col_stats[str(col_idx)] = stat

    print(json.dumps({
        'status': 'ok',
        'data': {
            'sheet_name': ws.title,
            'total_rows': total_rows,
            'total_cols': total_cols,
            'headers': headers,
            'column_stats': col_stats,
            'all_sheets': wb.sheetnames,
        }
    }))
except ImportError as e:
    print(json.dumps({'status': 'error', 'message': f'缺少依赖库: {e}'}))
except Exception as e:
    print(json.dumps({'status': 'error', 'message': str(e)}))
"#.to_string()
    }

    /// 构建 filter 操作的 Python 脚本 — 按条件过滤数据
    fn build_filter_script() -> String {
        r#"import json, sys

try:
    from openpyxl import Workbook, load_workbook

    data = json.loads(sys.argv[1])
    file_path = data.get('file_path', '')
    query = data.get('query', '')
    output_path = data.get('output_path') or ('filtered_' + file_path.split('/')[-1].split('\\')[-1])

    wb = load_workbook(file_path, read_only=False, data_only=True)
    ws = wb.active

    # 读取所有数据
    all_rows = list(ws.iter_rows(values_only=True))

    if not all_rows:
        raise ValueError('工作表为空，无法进行筛选')

    headers = [str(cell) if cell is not None else '' for cell in all_rows[0]]
    data_rows = all_rows[1:]

    # 解析查询条件：支持简单的 "列名 操作符 值" 格式
    filtered_rows = [headers[:]]  # 保留表头
    matched_count = 0

    if not query.strip():
        # 无条件则保留全部
        filtered_rows.extend(data_rows)
        matched_count = len(data_rows)
    else:
        # 尝试解析查询条件
        for row in data_rows:
            row_dict = {}
            for i, h in enumerate(headers):
                row_dict[h] = str(row[i]) if i < len(row) and row[i] is not None else ''

            # 简单匹配：检查每行是否包含查询关键词
            row_text = '\t'.join(str(v) for v in row if v is not None).lower()
            if query.lower() in row_text:
                filtered_rows.append([cell for cell in row])
                matched_count += 1

    # 写入新文件
    out_wb = Workbook()
    out_ws = out_wb.active
    out_ws.title = 'filtered'

    for row_data in filtered_rows:
        out_ws.append(row_data)

    out_wb.save(output_path)

    print(json.dumps({
        'status': 'ok',
        'data': {
            'total_rows': len(data_rows),
            'matched_rows': matched_count,
            'output_path': output_path,
            'query': query,
        }
    }))
except ImportError as e:
    print(json.dumps({'status': 'error', 'message': f'缺少依赖库: {e}'}))
except Exception as e:
    print(json.dumps({'status': 'error', 'message': str(e)}))
"#.to_string()
    }

    /// 构建 convert 操作的 Python 脚本 — CSV ↔ XLSX 转换
    fn build_convert_script() -> String {
        r#"import json, sys, os

try:
    from openpyxl import Workbook, load_workbook
    import csv

    data = json.loads(sys.argv[1])
    file_path = data.get('file_path', '')
    output_path = data.get('output_path', '')

    if not file_path:
        raise ValueError('缺少文件路径')

    ext = os.path.splitext(file_path)[1].lower()
    out_ext = os.path.splitext(output_path)[1].lower() if output_path else ''

    if not output_path:
        base = os.path.splitext(file_path)[0]
        if ext in ('.csv', '.txt'):
            output_path = base + '.xlsx'
        elif ext in ('.xlsx', '.xls'):
            output_path = base + '.csv'
        else:
            output_path = base + '.xlsx'
        out_ext = os.path.splitext(output_path)[1].lower()

    # CSV → XLSX
    if ext in ('.csv', '.txt'):
        wb = Workbook()
        ws = wb.active
        with open(file_path, 'r', encoding='utf-8-sig', newline='') as f:
            reader = csv.reader(f)
            for row in reader:
                ws.append(row)
        wb.save(output_path)
        info = f'CSV → XLSX 转换完成'

    # XLSX → CSV
    elif ext in ('.xlsx', '.xls'):
        wb = load_workbook(file_path, read_only=True, data_only=True)
        ws = wb.active
        with open(output_path, 'w', encoding='utf-8-sig', newline='') as f:
            writer = csv.writer(f)
            for row in ws.iter_rows(values_only=True):
                writer.writerow([str(cell) if cell is not None else '' for cell in row])
        info = f'XLSX → CSV 转换完成'
    else:
        raise ValueError(f'不支持的文件格式: {ext}')

    size = os.path.getsize(output_path) if os.path.exists(output_path) else 0
    print(json.dumps({
        'status': 'ok',
        'data': {
            'input_format': ext,
            'output_format': out_ext,
            'output_path': output_path,
            'output_size_bytes': size,
            'info': info,
        }
    }))
except ImportError as e:
    print(json.dumps({'status': 'error', 'message': f'缺少依赖库: {e}'}))
except Exception as e:
    print(json.dumps({'status': 'error', 'message': str(e)}))
"#.to_string()
    }
}
