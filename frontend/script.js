/**
 * EduBlitz 3-Tier — Enquiry form & enquiries list
 * POST /enquiries — submit | GET /enquiries — list
 */

const BACKEND_URL ='https://d2d4jknjifuryq.cloudfront.net';

const form = document.getElementById('enquiry-form');
const messageBox = document.getElementById('message-box');
const submitBtn = document.getElementById('submit-btn');
const refreshBtn = document.getElementById('refresh-btn');
const enquiriesCards = document.getElementById('enquiries-cards');
const enquiriesLoading = document.getElementById('enquiries-loading');
const enquiriesEmpty = document.getElementById('enquiries-empty');
const enquiriesError = document.getElementById('enquiries-error');

function showMessage(text, isError) {
    messageBox.textContent = text;
    messageBox.classList.remove('hidden', 'success', 'error');
    messageBox.classList.add(isError ? 'error' : 'success');
}

function hideMessage() {
    messageBox.classList.add('hidden');
    messageBox.classList.remove('success', 'error');
}

function formatDate(dateStr) {
    if (!dateStr) return '—';
    try {
        const d = new Date(dateStr);
        return d.toLocaleDateString(undefined, {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (_) {
        return dateStr;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

function renderEnquiries(list) {
    enquiriesCards.innerHTML = '';
    enquiriesLoading.classList.add('hidden');
    enquiriesError.classList.add('hidden');

    if (!list || list.length === 0) {
        enquiriesEmpty.classList.remove('hidden');
        return;
    }

    enquiriesEmpty.classList.add('hidden');

    list.forEach(function (item) {
        const card = document.createElement('article');
        card.className = 'enquiry-card';
        card.innerHTML =
            '<div class="enquiry-card-header">' +
            '<span class="enquiry-card-name">' + escapeHtml(item.name) + '</span>' +
            '<span class="enquiry-card-meta">' + escapeHtml(item.email) + ' · ' + formatDate(item.created_at) + '</span>' +
            '</div>' +
            '<div class="enquiry-card-course">' + escapeHtml(item.course) + '</div>' +
            '<div class="enquiry-card-message">' + escapeHtml(item.message) + '</div>';
        enquiriesCards.appendChild(card);
    });
}

function showEnquiriesError(msg) {
    enquiriesLoading.classList.add('hidden');
    enquiriesEmpty.classList.add('hidden');
    enquiriesError.textContent = msg || 'Could not load enquiries. Check backend URL.';
    enquiriesError.classList.remove('hidden');
    enquiriesCards.innerHTML = '';
}

async function loadEnquiries() {
    if (BACKEND_URL.includes('YOUR_EC2_PUBLIC_IP')) {
        enquiriesLoading.classList.add('hidden');
        enquiriesEmpty.classList.remove('hidden');
        enquiriesEmpty.textContent = 'Set BACKEND_URL in script.js to load enquiries.';
        enquiriesCards.innerHTML = '';
        return;
    }

    enquiriesLoading.classList.remove('hidden');
    enquiriesEmpty.classList.add('hidden');
    enquiriesError.classList.add('hidden');

    try {
        const response = await fetch(BACKEND_URL + '/enquiries');


        if (!response.ok) {
            showEnquiriesError('Backend returned ' + response.status);
            return;
        }
        const data = await response.json();
        if (!Array.isArray(data)) {
            showEnquiriesError('Invalid response from backend');
            return;
        }
        renderEnquiries(data);
    } catch (err) {
        showEnquiriesError('Could not reach backend. Check EC2 IP and that the server is running.');
    }
}

form.addEventListener('submit', async function (e) {
    e.preventDefault();
    hideMessage();

    if (BACKEND_URL.includes('YOUR_EC2_PUBLIC_IP')) {
        showMessage('Set BACKEND_URL in script.js to your EC2 public IP (e.g. http://54.123.45.67:8080)', true);
        return;
    }

    const name = document.getElementById('name').value.trim();
    const email = document.getElementById('email').value.trim();
    const course = document.getElementById('course').value.trim();
    const message = document.getElementById('message').value.trim();

    submitBtn.disabled = true;
    submitBtn.querySelector('.btn-text').textContent = 'Submitting…';

    try {
        const response = await fetch(BACKEND_URL + '/enquiry', {

 
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                name: name,
                email: email,
                course: course,
                message: message
            }).toString()
        });

        const data = await response.json().catch(function () { return {}; });

        if (response.ok && data.message) {
            showMessage('Enquiry submitted successfully', false);
            form.reset();
            loadEnquiries();
        } else {
            showMessage(data.error || data.message || 'Submission failed. Check backend and try again.', true);
        }
    } catch (err) {
        showMessage('Could not reach backend. Check EC2 IP and that the Java server is running on port 8080.', true);
    } finally {
        submitBtn.disabled = false;
        submitBtn.querySelector('.btn-text').textContent = 'Submit Enquiry';
    }
});

refreshBtn.addEventListener('click', function () {
    loadEnquiries();
});

loadEnquiries() 