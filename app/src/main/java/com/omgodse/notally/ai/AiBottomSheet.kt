package com.omgodse.notally.ai

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.omgodse.notally.databinding.DialogAiBottomSheetBinding

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

    private var _binding: DialogAiBottomSheetBinding? = null
    private val binding get() = _binding!!
    private var listener: Listener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DialogAiBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.GenerateButton.setOnClickListener {
            val prompt = binding.PromptInput.text?.toString()?.trim().orEmpty()
            if (prompt.isEmpty()) {
                Toast.makeText(requireContext(), "Enter a prompt", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            listener?.onGenerateFromPrompt(prompt)
            dismissAllowingStateLoss()
        }

        val disableActions = listener?.isNoteEmpty() == true
        binding.ActionSummarize.isEnabled = !disableActions
        binding.ActionEnhance.isEnabled = !disableActions
        binding.ActionProofread.isEnabled = !disableActions
        binding.ActionExtend.isEnabled = !disableActions

        binding.ActionSummarize.setOnClickListener {
            val count = listener?.noteWordCount() ?: 0
            if (count < 100) {
                Toast.makeText(requireContext(), "Add at least 100 words to summarize", Toast.LENGTH_SHORT).show()
            } else {
                listener?.onSummarize()
                dismissAllowingStateLoss()
            }
        }
        binding.ActionEnhance.setOnClickListener {
            listener?.onEnhance(); dismissAllowingStateLoss()
        }
        binding.ActionProofread.setOnClickListener {
            listener?.onProofread(); dismissAllowingStateLoss()
        }
        binding.ActionExtend.setOnClickListener {
            listener?.onExtend(); dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}