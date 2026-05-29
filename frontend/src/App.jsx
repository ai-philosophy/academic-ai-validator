import { useEffect, useRef, useState } from "react";
import {
  BookOpenCheck,
  ChevronDown,
  CheckSquare,
  ClipboardList,
  Download,
  Eraser,
  ExternalLink,
  FileSearch,
  Loader2,
  MessageSquareText,
  Search,
  Send,
  Sparkles,
  TriangleAlert
} from "lucide-react";
import { sendAcademicChat } from "./api/academicChat";

const fieldOptions = [
  "Học thuật tổng quát",
  "Giáo dục học",
  "Tâm lý học",
  "Khoa học máy tính",
  "Kinh tế học",
  "Xã hội học",
  "Y sinh học"
];

const academicLevelOptions = ["Đại học", "Sau đại học", "Phổ thông", "Nghiên cứu"];
const citationStyleOptions = ["APA", "MLA", "IEEE", "Chicago"];

const labelMaps = {
  risk: {
    LOW: "Thấp",
    MEDIUM: "Trung bình",
    HIGH: "Cao",
    CRITICAL: "Rất cao"
  },
  verdict: {
    LOW_RISK_TEXTUAL: "Ít rủi ro",
    MISSING_CITATION: "Thiếu bằng chứng",
    NEEDS_REVIEW: "Cần xem lại",
    NEEDS_SOURCE_CHECK: "Kiểm tra citation",
    OVERSTATED: "Lập luận quá mạnh",
    POSSIBLE_HALLUCINATION: "Cần xác minh nguồn"
  },
  type: {
    CONCEPT_DEFINITION: "Khái niệm",
    AUTHORSHIP_OR_SOURCE: "Tác giả/nguồn",
    STATISTIC_OR_DATA: "Số liệu",
    DATE_OR_HISTORY: "Mốc thời gian",
    CITATION: "Trích dẫn",
    CAUSAL_ARGUMENT: "Nhân quả",
    GENERALIZATION: "Tổng quát hóa",
    ACADEMIC_ARGUMENT: "Lập luận"
  },
  priority: {
    HIGH: "Cao",
    MEDIUM: "Trung bình",
    LOW: "Thấp"
  }
};

const sampleText = `Nghiên cứu cho thấy học tập cá nhân hóa luôn cải thiện kết quả của tất cả sinh viên.
Theo Smith (2020), 85% sinh viên học tốt hơn khi sử dụng AI trong lớp học.
Constructivism là lý thuyết cho rằng người học tự xây dựng tri thức thông qua trải nghiệm và tương tác xã hội.
Việc dùng chatbot trong học tập dẫn đến tăng động lực học tập và giảm hoàn toàn tình trạng trì hoãn.`;

const welcomeMessage = {
  id: "welcome",
  role: "assistant",
  text: "Chào bạn, gửi cho mình đoạn nội dung học thuật được tạo bởi AI. Mình sẽ tách claim, đánh dấu phần cần xem lại và tạo checklist kiểm định."
};

const emptyReview = {
  overallRisk: null,
  riskScore: 0,
  reviewedClaimCount: 0,
  claims: [],
  checklist: [],
  sourceSearchQueries: [],
  reviewNotes: []
};

export default function App() {
  const [field, setField] = useState(fieldOptions[0]);
  const [academicLevel, setAcademicLevel] = useState(academicLevelOptions[0]);
  const [citationStyle, setCitationStyle] = useState(citationStyleOptions[0]);
  const [message, setMessage] = useState("");
  const [messages, setMessages] = useState([welcomeMessage]);
  const [review, setReview] = useState(emptyReview);
  const [status, setStatus] = useState({ text: "Sẵn sàng", state: "idle" });
  const [isLoading, setIsLoading] = useState(false);
  const streamRef = useRef(null);
  const textareaRef = useRef(null);

  useEffect(() => {
    if (streamRef.current) {
      streamRef.current.scrollTop = streamRef.current.scrollHeight;
    }
  }, [messages, isLoading]);

  function resetTextareaHeight() {
    if (textareaRef.current) {
      textareaRef.current.style.height = "";
    }
  }

  function handleMessageChange(event) {
    setMessage(event.target.value);
    event.target.style.height = "auto";
    event.target.style.height = `${Math.min(event.target.scrollHeight, 220)}px`;
  }

  function handleClear() {
    setMessages([
      {
        id: crypto.randomUUID(),
        role: "assistant",
        text: "Đã làm mới hội thoại. Bạn gửi đoạn tiếp theo khi sẵn sàng."
      }
    ]);
    setReview(emptyReview);
    setStatus({ text: "Sẵn sàng", state: "idle" });
    setMessage("");
    resetTextareaHeight();
    textareaRef.current?.focus();
  }

  function handleSample() {
    setMessage(sampleText);
    requestAnimationFrame(() => {
      if (textareaRef.current) {
        textareaRef.current.focus();
        textareaRef.current.style.height = "auto";
        textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 220)}px`;
      }
    });
  }

  function handlePrompt(prompt) {
    setMessage(prompt);
    textareaRef.current?.focus();
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const trimmedMessage = message.trim();
    if (!trimmedMessage || isLoading) {
      return;
    }

    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: "user", text: trimmedMessage }
    ]);
    setMessage("");
    resetTextareaHeight();
    setIsLoading(true);
    setStatus({ text: "Đang phân tích", state: "working" });

    try {
      const data = await sendAcademicChat({
        message: trimmedMessage,
        field,
        academicLevel,
        citationStyle
      });

      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: "assistant",
          userPrompt: trimmedMessage,
          text: data.assistantMessage,
          review: data.review,
          context: {
            field,
            academicLevel,
            citationStyle
          },
          claimStatuses: createClaimStatuses(data.review),
          checklistStatuses: createChecklistStatuses(data.review),
          nextPrompts: data.nextPrompts || [],
          modelInfo: data
        }
      ]);
      setReview(data.review || emptyReview);
      setStatus({
        text: data.usedAiModel ? `AI: ${data.modelName}` : "Fallback rule-based",
        state: data.usedAiModel ? "done" : "working"
      });
    } catch (error) {
      setMessages((current) => [
        ...current,
        { id: crypto.randomUUID(), role: "assistant", text: error.message }
      ]);
      setStatus({ text: "Lỗi xử lý", state: "error" });
    } finally {
      setIsLoading(false);
    }
  }

  function handleComposerKeyDown(event) {
    if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  }

  function handleChecklistStatusChange(messageId, checklistId, statusValue) {
    setMessages((current) =>
      current.map((item) => {
        if (item.id !== messageId) {
          return item;
        }

        return {
          ...item,
          checklistStatuses: {
            ...(item.checklistStatuses || {}),
            [checklistId]: statusValue
          }
        };
      })
    );
  }

  function handleClaimStatusChange(messageId, claimId, statusValue) {
    setMessages((current) =>
      current.map((item) => {
        if (item.id !== messageId) {
          return item;
        }

        return {
          ...item,
          claimStatuses: {
            ...(item.claimStatuses || {}),
            [claimId]: statusValue
          }
        };
      })
    );
  }

  return (
    <main className="app-shell">
      <aside className="settings-panel" aria-label="Thiết lập học thuật">
        <div className="brand-block">
          <p className="eyebrow">Academic Chatbot</p>
          <h1>AI Academy</h1>
        </div>

        <SelectField label="Lĩnh vực" value={field} options={fieldOptions} onChange={setField} />
        <SelectField
          label="Cấp độ"
          value={academicLevel}
          options={academicLevelOptions}
          onChange={setAcademicLevel}
        />
        <SelectField
          label="Trích dẫn"
          value={citationStyle}
          options={citationStyleOptions}
          onChange={setCitationStyle}
        />

        <div className="side-actions">
          <button className="secondary-button" type="button" onClick={handleSample}>
            <Sparkles size={17} aria-hidden="true" />
            <span>Mẫu</span>
          </button>
          <button className="secondary-button" type="button" onClick={handleClear}>
            <Eraser size={17} aria-hidden="true" />
            <span>Xóa</span>
          </button>
        </div>

        <div className="status-panel">
          <span className="status-dot" data-state={status.state} />
          <span>{status.text}</span>
        </div>
      </aside>

      <section className="chat-panel" aria-label="Hội thoại kiểm định học thuật">
        <header className="chat-header">
          <div>
            <p className="eyebrow">AI Review Assistant</p>
            <h2>Kiểm định nội dung học thuật</h2>
          </div>
          <div className="risk-meter">
            <span className="risk-caption">Điểm rủi ro</span>
            <strong>
              <span className={riskClass(review.overallRisk)}>{review.riskScore}</span>
              <small>/100</small>
            </strong>
            <span className="risk-level">{labelMaps.risk[review.overallRisk] || "-"}</span>
          </div>
        </header>

        <div className="chat-stream" ref={streamRef} aria-live="polite">
          {messages.map((item) => (
            <ChatMessage
              key={item.id}
              message={item}
              onClaimStatusChange={handleClaimStatusChange}
              onChecklistStatusChange={handleChecklistStatusChange}
              onPrompt={handlePrompt}
            />
          ))}
          {isLoading && <TypingMessage />}
        </div>

        <form className="composer" onSubmit={handleSubmit}>
          <textarea
            ref={textareaRef}
            value={message}
            rows="3"
            placeholder="Dán nội dung AI cần kiểm định..."
            required
            disabled={isLoading}
            onChange={handleMessageChange}
            onKeyDown={handleComposerKeyDown}
          />
          <button className="primary-button" type="submit" disabled={isLoading}>
            {isLoading ? <Loader2 className="spin" size={18} aria-hidden="true" /> : <Send size={18} aria-hidden="true" />}
            <span>Gửi</span>
          </button>
        </form>
      </section>
    </main>
  );
}

function SelectField({ label, value, options, onChange }) {
  return (
    <div className="field-row">
      <label>{label}</label>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {options.map((option) => (
          <option key={option}>{option}</option>
        ))}
      </select>
    </div>
  );
}

function ChatMessage({ message, onClaimStatusChange, onChecklistStatusChange, onPrompt }) {
  if (message.role === "user") {
    return (
      <article className="message-row user-row">
        <div className="message-bubble user-bubble">
          <p>{message.text}</p>
        </div>
      </article>
    );
  }

  return (
    <article className="message-row bot-row">
      <div className="avatar" aria-hidden="true">
        AI
      </div>
      <div className="message-bubble bot-bubble">
        <p>{message.text}</p>
        {message.modelInfo && <ModelInfo modelInfo={message.modelInfo} />}
        {message.review && (
          <ReviewAccordion
            message={message}
            onClaimStatusChange={onClaimStatusChange}
            onChecklistStatusChange={onChecklistStatusChange}
          />
        )}
        {message.nextPrompts?.length > 0 && (
          <div className="prompt-row">
            {message.nextPrompts.map((prompt) => (
              <button
                className="prompt-chip"
                key={prompt}
                type="button"
                onClick={() => onPrompt(prompt)}
              >
                {prompt}
              </button>
            ))}
          </div>
        )}
      </div>
    </article>
  );
}

function ModelInfo({ modelInfo }) {
  const label = `${modelInfo.modelProvider}: ${modelInfo.modelName}`;

  return (
    <div className="model-info">
      <strong>{label}</strong>
      <span>{modelInfo.modelStatus || ""}</span>
    </div>
  );
}

function ReviewAccordion({ message, onClaimStatusChange, onChecklistStatusChange }) {
  const review = message.review;
  const highClaims = review.claims.filter((claim) => ["HIGH", "CRITICAL"].includes(claim.riskLevel)).length;

  return (
    <div className="review-accordion-shell">
      <button
        className="export-button"
        type="button"
        onClick={() => exportReviewWorkbook(message)}
      >
        <Download size={16} aria-hidden="true" />
        <span>Excel</span>
      </button>
      <details className="review-accordion">
        <summary>
          <span className="review-summary-title">
            <FileSearch size={16} aria-hidden="true" />
            <span>Chi tiết kiểm định</span>
          </span>
          <span className="inline-summary">
            <span>Claim: <strong>{review.reviewedClaimCount}</strong></span>
            <span>Rủi ro cao: <strong>{highClaims}</strong></span>
            <span>Checklist: <strong>{review.checklist.length}</strong></span>
          </span>
          <ChevronDown className="accordion-chevron" size={18} aria-hidden="true" />
        </summary>
        <ReviewContent
          checklistStatuses={message.checklistStatuses || {}}
          claimStatuses={message.claimStatuses || {}}
          messageId={message.id}
          review={review}
          highClaimCount={highClaims}
          onClaimStatusChange={onClaimStatusChange}
          onChecklistStatusChange={onChecklistStatusChange}
        />
      </details>
    </div>
  );
}

function TypingMessage() {
  return (
    <article className="message-row bot-row">
      <div className="avatar" aria-hidden="true">
        AI
      </div>
      <div className="message-bubble bot-bubble typing-bubble">
        <span />
        <span />
        <span />
      </div>
    </article>
  );
}

function ReviewContent({
  checklistStatuses,
  claimStatuses,
  highClaimCount,
  messageId,
  onClaimStatusChange,
  onChecklistStatusChange,
  review
}) {
  const sourceSuggestions = buildSourceSuggestions(review);

  return (
    <div className="inline-review-content">
      <div className="inline-review-section">
        <div className="metric-strip">
          <Metric label="Claim" value={review.reviewedClaimCount} icon={BookOpenCheck} />
          <Metric label="Rủi ro cao" value={highClaimCount} icon={TriangleAlert} />
          <Metric label="Checklist" value={review.checklist.length} icon={ClipboardList} />
        </div>
      </div>

      <section className="inline-review-section" aria-label="Claim kiểm chứng">
        <SectionHeading number="01" icon={FileSearch} title="Claim kiểm chứng" />
        {review.claims.length ? (
          review.claims.map((claim) => (
            <ClaimCard
              claim={claim}
              key={claim.id}
              messageId={messageId}
              status={claimStatuses[claim.id] || "pending"}
              onStatusChange={onClaimStatusChange}
            />
          ))
        ) : (
          <EmptyState text="Chưa có claim nào." />
        )}
      </section>

      <section className="inline-review-section" aria-label="Checklist rà soát">
        <SectionHeading number="02" icon={CheckSquare} title="Checklist rà soát" />
        {review.checklist.length ? (
          review.checklist.map((item) => (
            <ChecklistItem
              item={item}
              key={item.id}
              messageId={messageId}
              status={checklistStatuses[item.id] || "pending"}
              onStatusChange={onChecklistStatusChange}
            />
          ))
        ) : (
          <EmptyState text="Checklist sẽ xuất hiện sau khi bot kiểm định." />
        )}
      </section>

      <section className="inline-review-section" aria-label="Nguồn gợi ý">
        <SectionHeading number="03" icon={Search} title="Nguồn gợi ý" />
        {sourceSuggestions.length ? (
          <div className="source-grid">
            {sourceSuggestions.map((source) => (
              <SourceSuggestion source={source} key={`${source.claimId}-${source.url}-${source.title}`} />
            ))}
          </div>
        ) : (
          <EmptyState text="Chưa có link nguồn gợi ý cho lượt kiểm định này." />
        )}
      </section>
    </div>
  );
}

function SectionHeading({ icon: Icon, number, title }) {
  return (
    <div className="inline-section-heading">
      <span className="section-number">{number}</span>
      <Icon size={16} aria-hidden="true" />
      <h3>{title}</h3>
    </div>
  );
}

function Metric({ label, value, icon: Icon }) {
  return (
    <div className="metric-item">
      <Icon size={16} aria-hidden="true" />
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function ClaimCard({ claim, messageId, onStatusChange, status }) {
  return (
    <article className="claim-card">
      <div className="claim-head">
        <div>
          <div className="claim-id">{claim.id}</div>
          <p className="claim-text">{claim.text}</p>
        </div>
        <div className="claim-meta-row">
          <div className="badge-row">
            <span className="badge">{labelMaps.type[claim.type] || claim.type}</span>
            <span className={`badge ${riskClass(claim.riskLevel)}`}>
              {labelMaps.risk[claim.riskLevel] || claim.riskLevel} {claim.riskScore}
            </span>
            <span className="badge">{labelMaps.verdict[claim.verdict] || claim.verdict}</span>
          </div>
          <EditStatusRadios
            groupName={`${messageId}-${claim.id}-claim-status`}
            status={status}
            onChange={(statusValue) => onStatusChange(messageId, claim.id, statusValue)}
          />
        </div>
      </div>

      <div className="claim-body">
        <DetailBlock title="Lý do" items={claim.reasons} />
        <DetailBlock title="Câu hỏi rà soát" items={claim.reviewQuestions} />
      </div>

      {claim.evidenceSignals.length > 0 && <EvidenceBlock items={claim.evidenceSignals} />}
      <SuggestionBlock text={claim.suggestedRevision} />
    </article>
  );
}

function DetailBlock({ title, items }) {
  return (
    <div className="detail-block">
      <h3>{title}</h3>
      <ul>
        {items.length ? items.map((item) => <li key={item}>{item}</li>) : <li>Không có.</li>}
      </ul>
    </div>
  );
}

function SuggestionBlock({ text }) {
  return (
    <div className="suggestion-panel">
      <div className="suggestion-heading">
        <Sparkles size={15} aria-hidden="true" />
        <h3>Gợi ý chỉnh sửa</h3>
      </div>
      <p>{text || "Hãy đối chiếu nguồn học thuật trước khi sử dụng claim này."}</p>
    </div>
  );
}

function EvidenceBlock({ items }) {
  const groups = groupEvidenceItems(items);

  return (
    <div className="evidence-panel">
      <div className="evidence-heading">
        <Search size={15} aria-hidden="true" />
        <h3>Nguồn và bằng chứng</h3>
      </div>
      <p className="evidence-intro">
        Các mục dưới đây là tài liệu liên quan để kiểm tra, chưa tự động xác nhận claim là đúng.
      </p>
      {groups.sources.length > 0 && (
        <EvidenceGroup title="Nguồn gợi ý cần đọc" items={groups.sources} />
      )}
      {groups.cautions.length > 0 && (
        <EvidenceGroup title="Điểm cần đối chiếu" items={groups.cautions} />
      )}
      {groups.other.length > 0 && <EvidenceGroup title="Tín hiệu khác" items={groups.other} />}
    </div>
  );
}

function EvidenceGroup({ title, items }) {
  return (
    <div className="evidence-group">
      <h4>{title}</h4>
      <ul>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  );
}

function ChecklistItem({ item, messageId, onStatusChange, status }) {
  return (
    <article className="checklist-item">
      <div className="checklist-main">
        <div className="checklist-title-row">
          <span className="checklist-id">{item.id}</span>
          <h3>{item.title}</h3>
        </div>
        <p>{item.description}</p>
        <div className="checklist-tags">
          <span>{item.category}</span>
          <span>{item.relatedClaimIds.join(", ") || "Toàn bài"}</span>
        </div>
      </div>
      <div className="checklist-meta">
        <span className={`badge priority-${String(item.priority || "").toLowerCase()}`}>
          {labelMaps.priority[item.priority] || item.priority}
        </span>
        <EditStatusRadios
          groupName={`${messageId}-${item.id}-checklist-status`}
          status={status}
          onChange={(statusValue) => onStatusChange(messageId, item.id, statusValue)}
        />
      </div>
    </article>
  );
}

function SourceSuggestion({ source }) {
  return (
    <article className="source-card">
      <div className="source-card-head">
        <span>{source.claimId || "Tổng quan"}</span>
        <span>{source.type}</span>
      </div>
      <p>{source.title}</p>
      {source.detail && <small>{source.detail}</small>}
      <a href={source.url} target="_blank" rel="noreferrer">
        <ExternalLink size={14} aria-hidden="true" />
        <span>Mở nguồn</span>
      </a>
    </article>
  );
}

function EditStatusRadios({ groupName, onChange, status }) {
  return (
    <div className="edit-status-group" role="radiogroup" aria-label="Trạng thái chỉnh sửa">
      <label>
        <input
          checked={status === "pending"}
          name={groupName}
          type="radio"
          value="pending"
          onChange={() => onChange("pending")}
        />
        <span>Chưa chỉnh</span>
      </label>
      <label>
        <input
          checked={status === "edited"}
          name={groupName}
          type="radio"
          value="edited"
          onChange={() => onChange("edited")}
        />
        <span>Đã chỉnh</span>
      </label>
    </div>
  );
}

function EmptyState({ text }) {
  return (
    <div className="empty-state">
      <MessageSquareText size={18} aria-hidden="true" />
      <span>{text}</span>
    </div>
  );
}

function groupEvidenceItems(items) {
  return (items || []).reduce(
    (groups, item) => {
      const text = formatEvidenceText(item);
      const lower = text.toLowerCase();
      const isSource =
        lower.startsWith("gợi ý nguồn") ||
        lower.includes("openalex") ||
        lower.includes("crossref") ||
        lower.includes("doi:");
      const isCaution =
        lower.includes("chưa") ||
        lower.includes("không có") ||
        lower.includes("cần ") ||
        lower.includes("đối chiếu") ||
        lower.includes("kiểm tra") ||
        lower.includes("khớp");

      if (isSource) {
        groups.sources.push(text);
      } else if (isCaution) {
        groups.cautions.push(text);
      } else {
        groups.other.push(text);
      }
      return groups;
    },
    { sources: [], cautions: [], other: [] }
  );
}

function formatEvidenceText(value) {
  return String(value || "")
    .replaceAll("Nguồn ứng viên:", "Gợi ý nguồn liên quan:")
    .replaceAll("nguồn ứng viên", "gợi ý nguồn liên quan")
    .replaceAll("Nguồn ứng viên", "Gợi ý nguồn liên quan")
    .replaceAll("chỉ là ứng viên để đối chiếu", "chỉ là tài liệu liên quan để đối chiếu")
    .replaceAll("chưa xác nhận claim tuyệt đối", "chưa xác nhận được cách diễn đạt tuyệt đối của claim")
    .trim();
}

function buildSourceSuggestions(review) {
  const suggestions = [];

  (review.claims || []).forEach((claim) => {
    (claim.evidenceSignals || []).forEach((signal) => {
      const parsed = sourceFromText(signal, claim.id);
      if (parsed) {
        suggestions.push(parsed);
      }
    });
  });

  (review.reviewNotes || []).forEach((note) => {
    parseSourcesFromReviewNote(note).forEach((source) => suggestions.push(source));
  });

  (review.sourceSearchQueries || []).forEach((query, index) => {
    suggestions.push({
      claimId: "",
      detail: "Truy vấn tìm thêm nguồn học thuật",
      title: query,
      type: `Scholar ${index + 1}`,
      url: `https://scholar.google.com/scholar?q=${encodeURIComponent(query)}`
    });
  });

  return dedupeSources(suggestions).slice(0, 12);
}

function parseSourcesFromReviewNote(note) {
  const text = formatEvidenceText(note);
  if (!text.includes("Gợi ý nguồn liên quan") && !text.includes("OpenAlex/Crossref")) {
    return [];
  }

  const claimId = text.match(/^([A-Z]\d+):/)?.[1] || "";
  return text
    .split(" | ")
    .map((part) => sourceFromText(part, claimId))
    .filter(Boolean);
}

function sourceFromText(value, claimId) {
  const text = formatEvidenceText(value);
  const url = extractUrl(text);
  const doi = extractDoi(text);
  const sourceUrl = url || (doi ? `https://doi.org/${doi}` : "");
  if (!sourceUrl) {
    return null;
  }

  return {
    claimId,
    detail: doi ? `DOI: ${doi}` : "",
    title: extractSourceTitle(text, sourceUrl, doi),
    type: doi ? "DOI" : "Link",
    url: sourceUrl
  };
}

function extractUrl(value) {
  return value.match(/https?:\/\/[^\s)]+/i)?.[0]?.replace(/[.,;]+$/, "") || "";
}

function extractDoi(value) {
  return value.match(/\b10\.\d{4,9}\/[^\s,;)]+/i)?.[0]?.replace(/[.,;]+$/, "") || "";
}

function extractSourceTitle(value, url, doi) {
  return value
    .replace(/^([A-Z]\d+):\s*/, "")
    .replace(/^Gợi ý nguồn liên quan(?: từ OpenAlex\/Crossref để kiểm tra, chưa phải bằng chứng xác nhận)?:\s*/i, "")
    .replace(/^Gợi ý nguồn liên quan:\s*/i, "")
    .replace(url, "")
    .replace(doi ? `DOI: ${doi}` : "", "")
    .replace(/Cần đọc để kiểm tra claim\.?/i, "")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/[.,;:]+$/, "") || "Nguồn học thuật gợi ý";
}

function dedupeSources(sources) {
  const seen = new Set();
  return sources.filter((source) => {
    const key = source.url || source.title.toLowerCase();
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function riskClass(value) {
  return (
    {
      LOW: "risk-low",
      MEDIUM: "risk-medium",
      HIGH: "risk-high",
      CRITICAL: "risk-critical"
    }[value] || ""
  );
}

function createChecklistStatuses(review) {
  return Object.fromEntries((review?.checklist || []).map((item) => [item.id, "pending"]));
}

function createClaimStatuses(review) {
  return Object.fromEntries((review?.claims || []).map((item) => [item.id, "pending"]));
}

function exportReviewWorkbook(message) {
  const review = message.review;
  const claimStatuses = message.claimStatuses || {};
  const checklistStatuses = message.checklistStatuses || {};
  const workbookXml = buildExcelWorkbook([
    {
      name: "Tong quan",
      rows: [
        ["Mục", "Nội dung"],
        ["Prompt người dùng", message.userPrompt || ""],
        ["Hệ thống trả lời", message.text || ""],
        ["Lĩnh vực", message.context?.field || review.field || ""],
        ["Cấp độ", message.context?.academicLevel || review.academicLevel || ""],
        ["Chuẩn trích dẫn", message.context?.citationStyle || review.citationStyle || ""],
        ["Tóm tắt kiểm định", review.summary || ""],
        ["Mức rủi ro", labelMaps.risk[review.overallRisk] || review.overallRisk || ""],
        ["Điểm rủi ro", review.riskScore],
        ["Model", `${message.modelInfo?.modelProvider || ""}: ${message.modelInfo?.modelName || ""}`],
        ["Trạng thái model", message.modelInfo?.modelStatus || ""],
        ["Thời điểm export", new Date().toLocaleString("vi-VN")]
      ]
    },
    {
      name: "Can verify",
      rows: [
        [
          "ID",
          "Claim cần verify",
          "Loại",
          "Mức rủi ro",
          "Điểm",
          "Kết luận",
          "Lý do",
          "Câu hỏi rà soát",
          "Gợi ý chỉnh sửa",
          "Nguồn gợi ý / ghi chú bằng chứng",
          "Người dùng đánh dấu"
        ],
        ...review.claims.map((claim) => [
          claim.id,
          claim.text,
          labelMaps.type[claim.type] || claim.type,
          labelMaps.risk[claim.riskLevel] || claim.riskLevel,
          claim.riskScore,
          labelMaps.verdict[claim.verdict] || claim.verdict,
          joinExcelList(claim.reasons),
          joinExcelList(claim.reviewQuestions),
          claim.suggestedRevision || "",
          joinExcelList(claim.evidenceSignals.map(formatEvidenceText)),
          claimStatuses[claim.id] === "edited" ? "Đã chỉnh" : "Chưa chỉnh"
        ])
      ]
    },
    {
      name: "Checklist",
      rows: [
        [
          "ID",
          "Hạng mục",
          "Mô tả",
          "Ưu tiên",
          "Nhóm",
          "Claim liên quan",
          "Người dùng đánh dấu"
        ],
        ...review.checklist.map((item) => [
          item.id,
          item.title,
          item.description,
          labelMaps.priority[item.priority] || item.priority,
          item.category,
          item.relatedClaimIds.join(", ") || "Toàn bài",
          checklistStatuses[item.id] === "edited" ? "Đã chỉnh" : "Chưa chỉnh"
        ])
      ]
    },
    {
      name: "Nguon goi y",
      rows: [
        ["Loại", "Nội dung", "Link"],
        ...review.sourceSearchQueries.map((query, index) => [
          `Truy vấn ${index + 1}`,
          query,
          `https://scholar.google.com/scholar?q=${encodeURIComponent(query)}`
        ]),
        ...review.reviewNotes.map((note) => ["Ghi chú", formatEvidenceText(note), ""])
      ]
    }
  ]);

  downloadFile(
    workbookXml,
    `ai-academy-review-${new Date().toISOString().slice(0, 19).replaceAll(":", "-")}.xls`,
    "application/vnd.ms-excel;charset=utf-8"
  );
}

function buildExcelWorkbook(sheets) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:html="http://www.w3.org/TR/REC-html40">
 <Styles>
  <Style ss:ID="Header"><Font ss:Bold="1"/><Interior ss:Color="#DCEAF0" ss:Pattern="Solid"/></Style>
  <Style ss:ID="Wrap"><Alignment ss:Vertical="Top" ss:WrapText="1"/></Style>
 </Styles>
 ${sheets.map(buildWorksheet).join("")}
</Workbook>`;
}

function buildWorksheet(sheet) {
  return `<Worksheet ss:Name="${escapeXml(sheet.name.slice(0, 31))}">
  <Table>
   ${sheet.rows
     .map((row, rowIndex) => `<Row>${row.map((cell) => buildCell(cell, rowIndex === 0)).join("")}</Row>`)
     .join("")}
  </Table>
 </Worksheet>`;
}

function buildCell(value, isHeader) {
  const numeric = typeof value === "number" && Number.isFinite(value);
  return `<Cell ss:StyleID="${isHeader ? "Header" : "Wrap"}"><Data ss:Type="${numeric ? "Number" : "String"}">${escapeXml(value ?? "")}</Data></Cell>`;
}

function downloadFile(content, filename, type) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function joinExcelList(items) {
  return (items || []).filter(Boolean).join("\n");
}

function escapeXml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}
