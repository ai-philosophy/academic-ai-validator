# Luồng Hoạt Động Hệ Thống

Trường hợp mặc định: hệ thống luôn có GitHub Models token và luôn dùng GitHub Models để phân tích.

## Step By Step

1. Người dùng nhập prompt vào màn hình chat.

2. Frontend gửi nội dung đó về backend qua API:

```text
POST /api/academic-chat
```

3. Backend nhận prompt và chạy bước rule-based trước để tách nội dung thành các claim riêng.

4. Với từng claim, backend kiểm tra các dấu hiệu rủi ro như:

- thiếu citation;
- có số liệu/phần trăm;
- có tác giả/năm nhưng chưa rõ nguồn;
- dùng từ tuyệt đối như `luôn`, `tất cả`, `hoàn toàn`;
- claim nhân quả như `dẫn đến`, `cải thiện`, `làm tăng`, `giảm`.

5. Backend tìm nguồn gợi ý liên quan từ OpenAlex/Crossref cho các claim cần kiểm tra.

6. Backend gửi cho GitHub Models:

- prompt gốc của người dùng;
- danh sách claim đã tách;
- kết quả rule-based;
- nguồn gợi ý tìm được.

7. GitHub Models phân tích lại và trả về JSON gồm:

- mức rủi ro tổng thể;
- danh sách claim cần kiểm chứng;
- lý do vì sao claim rủi ro;
- câu hỏi người dùng cần rà soát;
- gợi ý chỉnh sửa;
- checklist;
- nguồn gợi ý liên quan.

8. Backend chuẩn hóa kết quả và trả response về frontend.

9. Frontend hiển thị kết quả ngay trong chat:

- Claim kiểm chứng;
- Checklist rà soát;
- Nguồn gợi ý;
- Gợi ý chỉnh sửa;
- nút đánh dấu `Chưa chỉnh` / `Đã chỉnh`;
- nút export Excel.

10. Khi người dùng export Excel, frontend tạo file chứa:

- prompt người dùng nhập;
- câu trả lời hệ thống;
- claim cần verify;
- checklist;
- trạng thái người dùng đã chỉnh;
- nguồn gợi ý và link.

## Tóm Tắt Ngắn

```text
User prompt
  -> Frontend gửi API
  -> Backend tách claim bằng rule-based
  -> Backend tìm nguồn OpenAlex/Crossref
  -> Backend gửi toàn bộ context cho GitHub Models
  -> GitHub Models phân tích và trả JSON
  -> Frontend hiển thị claim/checklist/nguồn
  -> Người dùng rà soát hoặc export Excel
```
