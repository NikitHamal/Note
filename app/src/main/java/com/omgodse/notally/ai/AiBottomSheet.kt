package com.omgodse.notally.ai

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.omgodse.notally.R

class AiBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onGenerateFromPrompt(prompt: String)
        fun onSummarize()
        fun onEnhance()
        fun onProofread()
        fun onExtend()
        fun isNoteEmpty(): Boolean
        fun noteWordCount(): Int
    }

    private var listener: Listener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_ai_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val promptEdit = view.findViewById<EditText>(R.id.PromptSingleLine)
        val generate = view.findViewById<View>(R.id.GenerateButton)
        val summarize = view.findViewById<View>(R.id.ActionSummarize)
        val enhance = view.findViewById<View>(R.id.ActionEnhance)
        val proofread = view.findViewById<View>(R.id.ActionProofread)
        val extend = view.findViewById<View>(R.id.ActionExtend)

        generate.setOnClickListener {
            val prompt = promptEdit.text?.toString()?.trim().orEmpty()
            if (prompt.isEmpty()) {
                Toast.makeText(requireContext(), "Enter a prompt", Toast.LENGTH_SHORT).show()
            } else {
                listener?.onGenerateFromPrompt(prompt)
                dismissAllowingStateLoss()
            }
        }

        val disable = listener?.isNoteEmpty() == true
        summarize.isEnabled = !disable
        enhance.isEnabled = !disable
        proofread.isEnabled = !disable
        extend.isEnabled = !disable

        summarize.setOnClickListener {
            val count = listener?.noteWordCount() ?: 0
            if (count < 100) {
                Toast.makeText(requireContext(), "Add at least 100 words to summarize", Toast.LENGTH_SHORT).show()
            } else {
                listener?.onSummarize()
                dismissAllowingStateLoss()
            }
        }
        enhance.setOnClickListener { listener?.onEnhance(); dismissAllowingStateLoss() }
        proofread.setOnClickListener { listener?.onProofread(); dismissAllowingStateLoss() }
        extend.setOnClickListener { listener?.onExtend(); dismissAllowingStateLoss() }
    }
}