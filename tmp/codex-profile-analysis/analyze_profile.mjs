import fs from "node:fs/promises";
import path from "node:path";
import { Workbook } from "@oai/artifact-tool";

if (process.argv[2] === "--all-pages") {
  const reportDirectory = "C:/study/portfolio-disclosure-guardian/foliolens-server/reports";
  const reportNames = (await fs.readdir(reportDirectory))
    .filter((name) => /^xml-structure-profile-page-\d{3}\.csv$/.test(name))
    .sort();

  const allDocumentIds = new Map();
  const aggregateStatusCounts = {};
  const aggregateSourceGroupCounts = {};
  const aggregateRootCounts = {};
  const aggregateRoleCounts = {};
  const pages = [];
  const failures = [];
  let totalRows = 0;
  let totalElapsedMillis = 0;
  let maxElapsed = null;

  const addCount = (target, key) => {
    const normalized = key === null || key === undefined || key === "" ? "(blank)" : String(key);
    target[normalized] = (target[normalized] ?? 0) + 1;
  };

  for (const reportName of reportNames) {
    const reportPath = path.join(reportDirectory, reportName);
    const reportText = (await fs.readFile(reportPath, "utf8")).replace(/^\uFEFF/, "");
    const pageWorkbook = await Workbook.fromCSV(reportText, { sheetName: "Profile" });
    const pageSheet = pageWorkbook.worksheets.getItem("Profile");
    const pageValues = pageSheet.getUsedRange(true).values;
    const pageHeaders = pageValues[0].map((value) => String(value));
    const pageRows = pageValues.slice(1).map((cells) =>
      Object.fromEntries(pageHeaders.map((header, index) => [header, cells[index]])),
    );

    let pageSuccessCount = 0;
    let pageFailedCount = 0;
    let pageElapsedMillis = 0;
    let strictlyAscending = true;

    for (let index = 0; index < pageRows.length; index++) {
      const row = pageRows[index];
      const documentId = String(row.disclosure_document_id ?? "");
      const status = String(row.status ?? "");
      const elapsedMillis = Number(row.elapsed_millis ?? 0);

      if (index > 0 && documentId <= String(pageRows[index - 1].disclosure_document_id ?? "")) {
        strictlyAscending = false;
      }

      const existingFiles = allDocumentIds.get(documentId) ?? [];
      existingFiles.push(reportName);
      allDocumentIds.set(documentId, existingFiles);

      addCount(aggregateStatusCounts, status);
      addCount(aggregateSourceGroupCounts, row.source_group);
      addCount(aggregateRootCounts, row.root_element_name);
      addCount(aggregateRoleCounts, row.document_role);

      if (status === "SUCCESS") pageSuccessCount++;
      if (status === "FAILED") {
        pageFailedCount++;
        failures.push({
          reportName,
          receiptNo: String(row.receipt_no ?? ""),
          fileName: String(row.file_name ?? ""),
          errorMessage: String(row.error_message ?? ""),
        });
      }

      pageElapsedMillis += elapsedMillis;
      if (maxElapsed === null || elapsedMillis > maxElapsed.elapsedMillis) {
        maxElapsed = {
          reportName,
          receiptNo: String(row.receipt_no ?? ""),
          fileName: String(row.file_name ?? ""),
          fileSizeBytes: Number(row.file_size_bytes ?? 0),
          elapsedMillis,
        };
      }
    }

    totalRows += pageRows.length;
    totalElapsedMillis += pageElapsedMillis;
    pages.push({
      reportName,
      rowCount: pageRows.length,
      successCount: pageSuccessCount,
      failedCount: pageFailedCount,
      firstDocumentId: String(pageRows[0]?.disclosure_document_id ?? ""),
      lastDocumentId: String(pageRows.at(-1)?.disclosure_document_id ?? ""),
      strictlyAscending,
      sumElapsedMillis: pageElapsedMillis,
    });
  }

  const duplicates = [...allDocumentIds.entries()]
    .filter(([, files]) => files.length > 1)
    .map(([documentId, files]) => ({ documentId, files }));

  const boundaryChecks = pages.slice(1).map((page, index) => ({
    previousPage: pages[index].reportName,
    currentPage: page.reportName,
    previousLastDocumentId: pages[index].lastDocumentId,
    currentFirstDocumentId: page.firstDocumentId,
    advances: pages[index].lastDocumentId < page.firstDocumentId,
  }));

  console.log(JSON.stringify({
    reportNames,
    pageCount: pages.length,
    pages,
    totalRows,
    uniqueDocumentIdCount: allDocumentIds.size,
    duplicateDocumentIdCount: duplicates.length,
    duplicates: duplicates.slice(0, 20),
    aggregateStatusCounts,
    aggregateSourceGroupCounts,
    aggregateRootCounts,
    aggregateRoleCounts,
    totalElapsedMillis,
    maxElapsed,
    boundaryChecks,
    failures,
  }, null, 2));

  process.exit(0);
}

const csvPath = process.argv[2]
  ?? "C:/study/portfolio-disclosure-guardian/foliolens-server/reports/xml-structure-profile-page-001.csv";
const csvText = (await fs.readFile(csvPath, "utf8")).replace(/^\uFEFF/, "");
const workbook = await Workbook.fromCSV(csvText, { sheetName: "Profile" });
const sheet = workbook.worksheets.getItem("Profile");
const usedRange = sheet.getUsedRange(true);

const inspection = await workbook.inspect({
  kind: "workbook,sheet,region",
  sheetId: "Profile",
  range: "A1:AH7",
  maxChars: 7000,
  tableMaxRows: 7,
  tableMaxCols: 34,
  tableMaxCellChars: 120,
});

const values = usedRange.values;
const headers = values[0].map((value) => String(value));
const rows = values.slice(1).map((cells) =>
  Object.fromEntries(headers.map((header, index) => [header, cells[index]])),
);

const number = (value) => {
  if (typeof value === "number") return value;
  if (value === null || value === undefined || value === "") return 0;
  return Number(value);
};

const text = (value) => (value === null || value === undefined ? "" : String(value));

const countBy = (field) =>
  Object.fromEntries(
    [...rows.reduce((map, row) => {
      const key = text(row[field]) || "(blank)";
      map.set(key, (map.get(key) ?? 0) + 1);
      return map;
    }, new Map())].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])),
  );

const sum = (field, subset = rows) =>
  subset.reduce((total, row) => total + number(row[field]), 0);

const stats = (field, subset = rows) => {
  const numbers = subset.map((row) => number(row[field])).sort((a, b) => a - b);
  if (numbers.length === 0) return null;
  const quantile = (q) => numbers[Math.min(numbers.length - 1, Math.floor((numbers.length - 1) * q))];
  return {
    min: numbers[0],
    median: quantile(0.5),
    p90: quantile(0.9),
    max: numbers.at(-1),
    average: Number((numbers.reduce((a, b) => a + b, 0) / numbers.length).toFixed(2)),
  };
};

const topBy = (field, limit = 5) =>
  [...rows]
    .sort((a, b) => number(b[field]) - number(a[field]))
    .slice(0, limit)
    .map((row) => ({
      receiptNo: text(row.receipt_no),
      reportName: text(row.report_name),
      fileName: text(row.file_name),
      sourceGroup: text(row.source_group),
      status: text(row.status),
      value: number(row[field]),
    }));

const successfulRows = rows.filter((row) => text(row.status) === "SUCCESS");
const failedRows = rows.filter((row) => text(row.status) === "FAILED");
const repairRows = successfulRows.filter(
  (row) => number(row.repaired_ampersand_count) + number(row.repaired_less_than_count) > 0,
);
const tableRows = successfulRows.filter((row) => number(row.table_count) > 0);

const structureFingerprint = (row) => [
  text(row.root_element_name),
  number(row.max_depth),
  number(row.distinct_tag_count),
  number(row.total_element_count),
  number(row.section_1_count),
  number(row.section_2_count),
  number(row.section_3_count),
  number(row.table_count),
].join("|");

const repeatedStructures = [...successfulRows.reduce((map, row) => {
  const key = structureFingerprint(row);
  const current = map.get(key) ?? {
    count: 0,
    sourceGroups: new Set(),
    documentNames: new Set(),
    exampleReceiptNo: text(row.receipt_no),
    maxDepth: number(row.max_depth),
    distinctTagCount: number(row.distinct_tag_count),
    totalElementCount: number(row.total_element_count),
    tableCount: number(row.table_count),
  };
  current.count += 1;
  current.sourceGroups.add(text(row.source_group));
  current.documentNames.add(text(row.document_name));
  map.set(key, current);
  return map;
}, new Map()).values()]
  .filter((item) => item.count > 1)
  .sort((a, b) => b.count - a.count)
  .slice(0, 10)
  .map((item) => ({
    ...item,
    sourceGroups: [...item.sourceGroups],
    documentNames: [...item.documentNames],
  }));

const groupSummary = Object.fromEntries(
  Object.keys(countBy("source_group")).map((group) => {
    const subset = successfulRows.filter((row) => text(row.source_group) === group);
    return [group, {
      count: rows.filter((row) => text(row.source_group) === group).length,
      successCount: subset.length,
      failedCount: rows.filter(
        (row) => text(row.source_group) === group && text(row.status) === "FAILED",
      ).length,
      averageElements: subset.length ? Number((sum("total_element_count", subset) / subset.length).toFixed(1)) : 0,
      averageTables: subset.length ? Number((sum("table_count", subset) / subset.length).toFixed(1)) : 0,
      averageElapsedMillis: subset.length ? Number((sum("elapsed_millis", subset) / subset.length).toFixed(1)) : 0,
      elementStats: stats("total_element_count", subset),
      tableStats: stats("table_count", subset),
      filesWithSection2: subset.filter((row) => number(row.section_2_count) > 0).length,
      filesWithSection3: subset.filter((row) => number(row.section_3_count) > 0).length,
      repairedFileCount: subset.filter(
        (row) => number(row.repaired_ampersand_count) + number(row.repaired_less_than_count) > 0,
      ).length,
    }];
  }),
);

const summary = {
  sourceFile: csvPath,
  dimensions: { rows: rows.length, columns: headers.length },
  headers,
  statusCounts: countBy("status"),
  sourceGroupCounts: countBy("source_group"),
  documentRoleCounts: countBy("document_role"),
  correctionCounts: countBy("correction"),
  contentFormatCounts: countBy("content_format"),
  rootElementCounts: countBy("root_element_name"),
  topRawSubtypes: Object.fromEntries(Object.entries(countBy("raw_subtype")).slice(0, 10)),
  groupSummary,
  successfulStructure: {
    missingDocumentNameCount: successfulRows.filter((row) => !text(row.document_name)).length,
    filesWithSection1: successfulRows.filter((row) => number(row.section_1_count) > 0).length,
    filesWithSection2: successfulRows.filter((row) => number(row.section_2_count) > 0).length,
    filesWithSection3: successfulRows.filter((row) => number(row.section_3_count) > 0).length,
    filesWithTitle: successfulRows.filter((row) => number(row.title_count) > 0).length,
    filesWithParagraph: successfulRows.filter((row) => number(row.paragraph_count) > 0).length,
    filesWithTable: tableRows.length,
    tablesWithoutRows: tableRows.filter((row) => number(row.table_row_count) === 0).length,
    tablesWithoutCells: tableRows.filter((row) => number(row.table_cell_count) === 0).length,
  },
  repairSummary: {
    affectedFileCount: repairRows.length,
    totalAmpersandRepairs: sum("repaired_ampersand_count", repairRows),
    totalLessThanRepairs: sum("repaired_less_than_count", repairRows),
  },
  executionSummary: {
    sumOfFileElapsedMillis: sum("elapsed_millis"),
    averageAllRowsMillis: Number((sum("elapsed_millis") / rows.length).toFixed(1)),
  },
  attachmentSummary: {
    groupCounts: Object.fromEntries(
      rows.filter((row) => text(row.document_role) === "ATTACHMENT")
        .reduce((map, row) => {
          const key = text(row.source_group);
          map.set(key, (map.get(key) ?? 0) + 1);
          return map;
        }, new Map()),
    ),
    documentNameCounts: Object.fromEntries(
      [...rows.filter((row) => text(row.document_role) === "ATTACHMENT")
        .reduce((map, row) => {
          const key = text(row.document_name) || "(blank)";
          map.set(key, (map.get(key) ?? 0) + 1);
          return map;
        }, new Map())]
        .sort((a, b) => b[1] - a[1]),
    ),
  },
  repeatedStructures,
  numericStats: {
    fileSizeBytes: stats("file_size_bytes", successfulRows),
    elapsedMillis: stats("elapsed_millis", successfulRows),
    maxDepth: stats("max_depth", successfulRows),
    distinctTagCount: stats("distinct_tag_count", successfulRows),
    totalElementCount: stats("total_element_count", successfulRows),
    tableCount: stats("table_count", successfulRows),
  },
  topOutliers: {
    elapsedMillis: topBy("elapsed_millis"),
    fileSizeBytes: topBy("file_size_bytes"),
    totalElementCount: topBy("total_element_count"),
    maxDepth: topBy("max_depth"),
    tableCount: topBy("table_count"),
    repairedAmpersandCount: topBy("repaired_ampersand_count"),
    repairedLessThanCount: topBy("repaired_less_than_count"),
  },
  failures: failedRows.map((row) => ({
    receiptNo: text(row.receipt_no),
    reportName: text(row.report_name),
    fileName: text(row.file_name),
    relativePath: text(row.relative_path),
    fileSizeBytes: number(row.file_size_bytes),
    elapsedMillis: number(row.elapsed_millis),
    errorType: text(row.error_type),
    errorLine: number(row.error_line),
    errorColumn: number(row.error_column),
    errorMessage: text(row.error_message),
  })),
};

const datasetRoot = "C:/study/portfolio-disclosure-guardian/foliolens-data";
const failureContexts = [];

for (const failure of summary.failures) {
  const sourcePath = path.resolve(datasetRoot, failure.relativePath);
  const sourceText = await fs.readFile(sourcePath, "utf8");
  const sourceLines = sourceText.split(/\r\n|\n|\r/);
  const rawLine = sourceLines[failure.errorLine - 1] ?? "";
  const tagName = failure.errorMessage.match(/element type \"([^\"]+)\"/i)?.[1] ?? "";
  const tagIndex = tagName ? rawLine.indexOf(`<${tagName}`) : -1;
  const approximateIndex = tagIndex >= 0
    ? tagIndex
    : Math.max(0, failure.errorColumn - 1);
  const snippetStart = Math.max(0, approximateIndex - 180);
  const snippetEnd = Math.min(rawLine.length, approximateIndex + 260);

  failureContexts.push({
    receiptNo: failure.receiptNo,
    sourcePath,
    errorLine: failure.errorLine,
    reportedColumn: failure.errorColumn,
    rawLineLength: rawLine.length,
    tagName,
    tagColumnInRawLine: tagIndex >= 0 ? tagIndex + 1 : null,
    snippet: rawLine.slice(snippetStart, snippetEnd),
    nearbyLines: sourceLines
      .slice(Math.max(0, failure.errorLine - 4), failure.errorLine + 1)
      .map((line, index) => ({
        lineNumber: Math.max(1, failure.errorLine - 3) + index,
        text: line.length > 600 ? `${line.slice(0, 600)}…` : line,
      })),
  });
}

console.log("---INSPECTION---");
console.log(inspection.ndjson);
console.log("---FAILURE-SUMMARY---");
console.log(JSON.stringify({
  dimensions: summary.dimensions,
  statusCounts: summary.statusCounts,
  pageBoundary: {
    firstDocumentId: text(rows[0]?.disclosure_document_id),
    firstReceiptNo: text(rows[0]?.receipt_no),
    lastDocumentId: text(rows.at(-1)?.disclosure_document_id),
    lastReceiptNo: text(rows.at(-1)?.receipt_no),
  },
  sourceGroupCounts: summary.sourceGroupCounts,
  documentRoleCounts: summary.documentRoleCounts,
  rootElementCounts: summary.rootElementCounts,
  groupSummary: summary.groupSummary,
  successfulStructure: summary.successfulStructure,
  repairSummary: summary.repairSummary,
  executionSummary: summary.executionSummary,
  numericStats: summary.numericStats,
  topOutliers: summary.topOutliers,
  failures: summary.failures,
  failureContexts,
}, null, 2));
