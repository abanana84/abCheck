#!/usr/bin/env python3
"""Generate diagnostic UI string resources from the reviewed English/Vietnamese label table."""
from pathlib import Path
from xml.sax.saxutils import escape

rows = '''Dashboard|Tổng quan
Hardware|Phần cứng
System|Hệ thống
Battery|Pin
Network|Mạng
Apps|Ứng dụng
Camera|Camera
Sensors|Cảm biến
Tests|Kiểm tra
Tools|Công cụ
Widgets|Tiện ích
Monitors|Theo dõi
Benchmarks|Hiệu năng
Battery monitor|Theo dõi pin
Device report|Báo cáo thiết bị
Settings|Cài đặt
Privacy policy|Chính sách quyền riêng tư
Language|Ngôn ngữ
CPU status|Trạng thái CPU
Virtual CPU|CPU ảo
Offline|Ngừng hoạt động
Frequency unavailable|Không đọc được xung nhịp
RAM|RAM
Storage|Bộ nhớ lưu trữ
Display|Màn hình
CPU analysis|Phân tích CPU
System graphs|Đồ thị hệ thống
Temperatures|Nhiệt độ
Wi-Fi scan|Quét Wi-Fi
Bluetooth scan|Quét Bluetooth
USB|USB
Network usage|Lưu lượng mạng
Screen time|Thời gian sử dụng
Permissions explorer|Quản lý quyền ứng dụng
Task manager|Tiến trình
Logcat|Nhật ký ứng dụng
OpenGL info|Thông tin OpenGL
Network reachability|Kiểm tra kết nối
Network mapper|Quét mạng nội bộ
Shizuku diagnostics|Chẩn đoán qua Shizuku
Root check|Kiểm tra root
Boot integrity|Tính toàn vẹn khởi động
CPU times|Thời gian CPU
GPU times|Thời gian GPU
Storage health|Tình trạng bộ nhớ
Display test|Kiểm tra màn hình
Multitouch|Cảm ứng đa điểm
Brightness|Độ sáng
Buttons|Phím cứng
Vibration|Rung
Speakers|Loa ngoài
Earpiece|Loa thoại
Microphone|Micro
Flashlight|Đèn pin
Camera test|Kiểm tra camera
Accelerometer|Gia tốc kế
Gyroscope|Con quay hồi chuyển
Light sensor|Cảm biến ánh sáng
Proximity|Cảm biến tiệm cận
Magnetic field|Từ trường
Headset|Tai nghe
Charging|Sạc pin
Biometric scanner|Cảm biến sinh trắc học
Processor|Bộ xử lý
CPU frequencies|Tần số CPU
CPU information|Thông tin CPU
Graphics|Đồ họa
Memory and storage|RAM và bộ nhớ
Kernel memory|Bộ nhớ hệ điều hành
Audio devices|Thiết bị âm thanh
Media codecs|Bộ mã hóa âm thanh và video
Hardware features|Tính năng phần cứng
Device|Thiết bị
Android|Android
Runtime|Trạng thái hoạt động
Root and privileged access|Root và quyền nâng cao
Connection|Kết nối
Wi-Fi|Wi-Fi
Mobile network|Mạng di động
Traffic since boot|Lưu lượng từ khi khởi động
Manufacturer|Nhà sản xuất
Brand|Thương hiệu
Model|Mẫu máy
Product|Sản phẩm
Board|Bo mạch
Bootloader|Bộ nạp khởi động
Version|Phiên bản
API level|Cấp API
Security patch|Bản vá bảo mật
Build|Bản dựng
Build ID|Mã bản dựng
Fingerprint|Dấu vân tay bản dựng
Build type|Loại bản dựng
Tags|Thẻ bản dựng
Kernel|Nhân hệ điều hành
Time zone|Múi giờ
Uptime|Thời gian từ khi khởi động
Awake time|Thời gian hoạt động
Deep sleep since boot|Thời gian ngủ sâu
Power saving|Tiết kiệm pin
Interactive|Màn hình đang hoạt động
Thermal status|Trạng thái nhiệt
ADB enabled|Đã bật ADB
Developer options|Tùy chọn nhà phát triển
SoC manufacturer|Nhà sản xuất SoC
SoC model|Mẫu SoC
Cores|Số lõi
Architecture|Kiến trúc
64-bit ABIs|ABI 64 bit
Resolution|Độ phân giải
Refresh rate|Tần số quét
Density|Mật độ điểm ảnh
Logical density|Mật độ logic
Wide color gamut|Dải màu rộng
Supported modes|Chế độ hỗ trợ
Java heap limit|Giới hạn bộ nhớ Java
Internal storage|Bộ nhớ trong
Available storage|Bộ nhớ trống
Page size|Kích thước trang
External volumes|Bộ nhớ ngoài
Transport|Loại kết nối
Internet validated|Đã xác nhận Internet
Metered|Tính phí dữ liệu
Downstream estimate|Ước tính tốc độ tải xuống
Upstream estimate|Ước tính tốc độ tải lên
Interface|Giao diện mạng
Addresses|Địa chỉ
Routes|Định tuyến
Enabled|Đang bật
SSID|SSID
BSSID|BSSID
Signal|Tín hiệu
Link speed|Tốc độ liên kết
Frequency|Tần số
Received|Đã nhận
Sent|Đã gửi
Mobile received|Dữ liệu di động nhận
Mobile sent|Dữ liệu di động gửi
This app received|Ứng dụng này nhận
This app sent|Ứng dụng này gửi
Operator|Nhà mạng
Country|Quốc gia
SIM operator|Nhà mạng SIM
SIM state|Trạng thái SIM
Phone type|Loại điện thoại
Roaming|Chuyển vùng
Vendor|Nhà cung cấp
Type|Loại
Range|Phạm vi
Power|Công suất
Minimum delay|Độ trễ tối thiểu
Wake-up|Đánh thức thiết bị
Reporting mode|Chế độ báo cáo
Status|Trạng thái
Access|Quyền truy cập
Scope|Phạm vi
Unavailable|Không khả dụng
Disconnected|Đã ngắt kết nối
Yes|Có
No|Không
Normal|Bình thường
Live sensor readings|Dữ liệu cảm biến trực tiếp
Waiting for sensor events…|Đang chờ dữ liệu cảm biến…
Search information|Tìm kiếm thông tin
Search apps or packages|Tìm ứng dụng hoặc tên gói
User|Người dùng
All|Tất cả
App settings|Cài đặt ứng dụng
Close|Đóng
OK|OK
Open test|Mở bài kiểm tra
Open tool|Mở công cụ
Run test|Chạy kiểm tra
Toggle flash|Bật hoặc tắt đèn pin
Ready|Sẵn sàng
Appearance|Giao diện
Android access|Quyền truy cập Android
App permissions|Quyền ứng dụng
Usage access|Quyền truy cập sử dụng
Display over other apps|Hiển thị trên ứng dụng khác
Share report|Chia sẻ báo cáo
Export a device report|Xuất báo cáo thiết bị
Choose where to save|Chọn nơi lưu
Copy quick specs|Sao chép thông số chính
Copied to clipboard|Đã sao chép
Report exported|Đã xuất báo cáo
Camera images are not saved|Không lưu hình ảnh camera
Start recording|Bắt đầu ghi
Stop recording|Dừng ghi
Play recording|Phát bản ghi
Scan Wi-Fi|Quét Wi-Fi
Scan Bluetooth|Quét Bluetooth
Scan local network|Quét mạng nội bộ
Check connectivity|Kiểm tra kết nối
Hostname or IP|Tên miền hoặc IP
Cancel|Hủy
Cancelled|Đã hủy
Complete|Hoàn tất
Start overlay|Bật cửa sổ nổi
Stop|Dừng
Floating monitor|Cửa sổ theo dõi nổi
Live system graphs|Đồ thị hệ thống trực tiếp
Home screen widget|Tiện ích màn hình chính
Add dashboard widget|Thêm tiện ích tổng quan
Refresh widgets|Làm mới tiện ích
Battery history|Lịch sử pin
Copy history CSV|Sao chép lịch sử dạng CSV
Enable usage access|Bật quyền truy cập sử dụng
Refresh|Làm mới
Location settings|Cài đặt vị trí
No data reported by this device.|Thiết bị không cung cấp dữ liệu.
No USB devices connected|Chưa kết nối thiết bị USB
Run benchmark|Đo hiệu năng
Local performance benchmark|Đo hiệu năng cục bộ
Finished runs will appear here.|Kết quả đo sẽ xuất hiện tại đây.
This tool needs permission|Công cụ này cần quyền truy cập
Grant permission|Cấp quyền
Open app settings|Mở cài đặt ứng dụng
Open Shizuku|Mở Shizuku
Grant access|Cấp quyền truy cập
Connect|Kết nối
Thermal sensors|Cảm biến nhiệt
All processes|Tất cả tiến trình
Device logs|Nhật ký thiết bị
Battery service|Dịch vụ pin
Root access check|Kiểm tra quyền root
Test root access|Thử quyền root
Boot and identity signals|Thông tin khởi động và nhận dạng
Hardware verification|Xác minh phần cứng
Verified boot state|Trạng thái khởi động đã xác minh
Flash locked|Khóa flash
Device state|Trạng thái thiết bị
Build fingerprint|Dấu vân tay bản dựng
Build tags|Thẻ bản dựng
Operating System|Hệ điều hành
CPU Configuration|Cấu hình CPU
IP configuration|Cấu hình IP
Battery details|Chi tiết pin
Reports|Báo cáo
Visible|Hiển thị
Temperature|Nhiệt độ
Pressure|Áp suất
Gravity|Trọng lực
Linear acceleration|Gia tốc tuyến tính
Rotation vector|Véc-tơ xoay
Humidity|Độ ẩm
'''
entries=[line.split('|',1) for line in rows.strip().splitlines()]
base=Path(__file__).resolve().parents[1]
for folder,column in [('values',0),('values-vi',1)]:
    body=['<?xml version="1.0" encoding="utf-8"?>','<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="MissingTranslation">']
    for index,pair in enumerate(entries):
        body.append(f'    <string name="device_label_{index}" formatted="false">{escape(pair[column]).replace(chr(39),chr(92)+chr(39))}</string>')
    body.append('</resources>')
    (base/'app/src/main/res'/folder/'device_strings.xml').write_text('\n'.join(body)+'\n')
lines=['package com.abanana.abattery.presentation.device','','import androidx.compose.runtime.Composable','import androidx.compose.ui.res.stringResource','import com.abanana.abattery.R','','private val diagnosticLabels = mapOf(']
for i,(en,_) in enumerate(entries):
    lines.append(f'    "{en}" to R.string.device_label_{i},')
lines+=[')','','@Composable','fun deviceString(text: String): String = diagnosticLabels[text]?.let { stringResource(it) } ?: text','']
(base/'app/src/main/java/com/abanana/abattery/presentation/device/DeviceStrings.kt').write_text('\n'.join(lines))
